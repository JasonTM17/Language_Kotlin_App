package com.linguaai.server.repository

import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.api.dto.ActivityDayDto
import com.linguaai.server.api.dto.ProgressEventTypes
import com.linguaai.server.api.dto.ProgressSummaryDto
import com.linguaai.server.api.dto.ProgressTotalsDto
import com.linguaai.server.api.dto.RecordProgressEventRequest
import com.linguaai.server.api.dto.RecordProgressEventResponse
import com.linguaai.server.api.dto.StreakDto
import com.linguaai.server.api.dto.VocabularyProgressDto
import com.linguaai.server.api.dto.VocabularyProgressItemDto
import com.linguaai.server.api.dto.VocabularyProgressSnapshotDto
import com.linguaai.server.api.dto.WeakTopicDto
import com.linguaai.server.db.AiConversations
import com.linguaai.server.db.LearningStreaks
import com.linguaai.server.db.QuizAttempts
import com.linguaai.server.db.UserMistakes
import com.linguaai.server.db.UserProgress
import com.linguaai.server.db.UserVocabularyProgress
import com.linguaai.server.db.Vocabularies
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Progress aggregation and event recording.
 *
 * Two invariants this class owns:
 *
 * 1. **Idempotency** — `recordEvent` is keyed on `(userId, clientOperationId)`
 *    through `uq_progress_operation`. Replaying a request returns the existing
 *    row with `created = false` instead of inserting a duplicate. This is what
 *    makes offline retry/backoff safe, so the unique index is load-bearing and
 *    must not be dropped.
 *
 * 2. **Streak is derived, never counted** — the streak is computed from the set
 *    of recorded activity days, so a drifting counter or a device clock change
 *    cannot inflate it.
 */
class ProgressRepository {
    /** Longest window of activity days considered when computing streaks. */
    private val streakWindowDays = 400L

    /** Days of history returned for the activity strip. */
    private val activityWindowDays = 14

    /** Maximum number of weak topics surfaced. */
    private val weakTopicLimit = 5

    // Truncation limits. These have to match the column widths in db/Tables.kt:
    // a value longer than the column makes the insert fail rather than clipping,
    // so the two are coupled and both sides say so.
    private val maxOperationIdLength = 64
    private val maxEventTypeLength = 30

    /** A single event cannot credibly claim more than a full day of study. */
    private val maxEventMinutes = 1440
    private val maxIntAsLong = Int.MAX_VALUE.toLong()

    /** MySQL's error code for a duplicate-key insert. */
    private val mysqlDuplicateKeyError = 1062

    /**
     * Duplicate-key detection for the idempotency races (two devices replaying
     * one operation, or two events opening the same streak day). MySQL raises
     * 1062 inside a SQLIntegrityConstraintViolationException.
     */
    private fun Throwable.isDuplicateKey(): Boolean {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is java.sql.SQLIntegrityConstraintViolationException) return true
            if (cause is java.sql.SQLException && cause.errorCode == mysqlDuplicateKeyError) return true
            cause = cause.cause
        }
        return false
    }

    // Mastery bands on the shared 0..5 scale. The Android SRS scheduler uses the
    // same scale, so a change here has to be mirrored there — which is only
    // possible if the numbers are findable.
    private val masteryLearnedFloor = 4
    private val learningBand = 1..3

    private data class VocabularyProgressTimes(
        val lastReviewedAt: LocalDateTime?,
        val nextReviewAt: LocalDateTime?,
        val clientUpdatedAt: LocalDateTime?,
        val now: LocalDateTime,
    )

    /**
     * Records one learning event. Only [ProgressEventTypes.all] count toward a
     * streak; anything else is stored but does not extend activity.
     */
    fun recordEvent(
        userId: Long,
        request: RecordProgressEventRequest,
    ): RecordProgressEventResponse =
        transaction {
            val existing =
                UserProgress
                    .selectAll()
                    .andWhere { UserProgress.userId eq userId }
                    .andWhere { UserProgress.clientOperationId eq request.clientOperationId }
                    .firstOrNull()
            if (existing != null) {
                return@transaction RecordProgressEventResponse(
                    eventId = existing[UserProgress.id],
                    created = false,
                )
            }

            val now = LocalDateTime.now()
            val isMeaningful = request.eventType in ProgressEventTypes.all
            // A single event claiming more than a full day of minutes is a
            // client bug; capping keeps totals inside Int range even when the
            // daily strip sums many events.
            val minutes = if (isMeaningful) request.minutes.coerceIn(0, maxEventMinutes) else 0

            val eventId =
                try {
                    UserProgress.insert { row ->
                        row[UserProgress.userId] = userId
                        row[UserProgress.clientOperationId] = request.clientOperationId.take(maxOperationIdLength)
                        row[UserProgress.eventType] = request.eventType.take(maxEventTypeLength)
                        row[UserProgress.refId] = request.refId
                        row[UserProgress.minutes] = minutes
                        row[UserProgress.occurredAt] = now
                        row[UserProgress.createdAt] = now
                    } get UserProgress.id
                } catch (duplicate: ExposedSQLException) {
                    // Two devices retrying the same operation race the
                    // select-then-insert. A unique-index violation IS the
                    // replay signal: answer with the stored event instead of
                    // a 500.
                    if (!duplicate.isDuplicateKey()) throw duplicate
                    val replayed =
                        UserProgress
                            .selectAll()
                            .andWhere { UserProgress.userId eq userId }
                            .andWhere { UserProgress.clientOperationId eq request.clientOperationId }
                            .firstOrNull()
                            ?: throw duplicate
                    return@transaction RecordProgressEventResponse(
                        eventId = replayed[UserProgress.id],
                        created = false,
                    )
                }

            if (isMeaningful) {
                creditActivityDay(userId, LocalDate.now(), minutes)
            }

            request.vocabularyProgress?.let { snapshot ->
                upsertVocabularyProgress(
                    userId = userId,
                    vocabularyId = requireVocabulary(request.refId),
                    snapshot = snapshot,
                    eventType = request.eventType,
                    now = now,
                )
            }

            RecordProgressEventResponse(eventId = eventId, created = true)
        }

    /** Applies a flashcard snapshot only after the idempotent event row exists. */
    private fun upsertVocabularyProgress(
        userId: Long,
        vocabularyId: Long,
        snapshot: VocabularyProgressSnapshotDto,
        eventType: String,
        now: LocalDateTime,
    ) {
        val existing =
            UserVocabularyProgress
                .selectAll()
                .andWhere { UserVocabularyProgress.userId eq userId }
                .andWhere { UserVocabularyProgress.vocabularyId eq vocabularyId }
                .forUpdate()
                .firstOrNull()
        val times =
            VocabularyProgressTimes(
                lastReviewedAt = toDatabaseTime(snapshot.lastReviewedAtEpochMillis),
                nextReviewAt = toDatabaseTime(snapshot.nextReviewAtEpochMillis),
                clientUpdatedAt = toDatabaseTime(snapshot.stateUpdatedAtEpochMillis),
                now = now,
            )

        if (existing == null) {
            if (insertVocabularyProgress(userId, vocabularyId, snapshot, times)) return
            // Another transaction may have inserted the unique (user, word) row
            // after the first select. Re-read it under a row lock and merge the
            // concurrent snapshot instead of surfacing a duplicate-key failure.
            val concurrent =
                UserVocabularyProgress
                    .selectAll()
                    .andWhere { UserVocabularyProgress.userId eq userId }
                    .andWhere { UserVocabularyProgress.vocabularyId eq vocabularyId }
                    .forUpdate()
                    .firstOrNull()
            if (concurrent != null) {
                updateVocabularyProgress(concurrent, snapshot, eventType, times)
            }
        } else {
            updateVocabularyProgress(
                existing = existing,
                snapshot = snapshot,
                eventType = eventType,
                times = times,
            )
        }
    }

    private fun insertVocabularyProgress(
        userId: Long,
        vocabularyId: Long,
        snapshot: VocabularyProgressSnapshotDto,
        times: VocabularyProgressTimes,
    ): Boolean {
        val result =
            UserVocabularyProgress.insertIgnore { row ->
                row[UserVocabularyProgress.userId] = userId
                row[UserVocabularyProgress.vocabularyId] = vocabularyId
                row[UserVocabularyProgress.favorite] = snapshot.favorite
                row[UserVocabularyProgress.masteryLevel] = snapshot.masteryLevel
                row[UserVocabularyProgress.reviewCount] = snapshot.reviewCount
                row[UserVocabularyProgress.correctCount] = snapshot.correctCount
                row[UserVocabularyProgress.wrongCount] = snapshot.wrongCount
                row[UserVocabularyProgress.lastReviewedAt] = times.lastReviewedAt
                row[UserVocabularyProgress.nextReviewAt] = times.nextReviewAt
                row[UserVocabularyProgress.clientUpdatedAt] = times.clientUpdatedAt
                row[UserVocabularyProgress.updatedAt] = times.now
            }
        return result.insertedCount > 0
    }

    private fun updateVocabularyProgress(
        existing: ResultRow,
        snapshot: VocabularyProgressSnapshotDto,
        eventType: String,
        times: VocabularyProgressTimes,
    ) {
        val reviewIsOlder = isReviewSnapshotOlder(existing, snapshot, times.lastReviewedAt)
        val existingClientUpdatedAt = existing[UserVocabularyProgress.clientUpdatedAt]
        val favoriteIsOlder = isClientStateOlder(existingClientUpdatedAt, times.clientUpdatedAt)
        if (shouldIgnoreSnapshot(eventType, reviewIsOlder, favoriteIsOlder)) return

        val id = existing[UserVocabularyProgress.id]
        UserVocabularyProgress.update({ UserVocabularyProgress.id eq id }) { row ->
            if (!favoriteIsOlder) row[UserVocabularyProgress.favorite] = snapshot.favorite
            if (!reviewIsOlder) {
                row[UserVocabularyProgress.masteryLevel] = snapshot.masteryLevel
                row[UserVocabularyProgress.reviewCount] = snapshot.reviewCount
                row[UserVocabularyProgress.correctCount] = snapshot.correctCount
                row[UserVocabularyProgress.wrongCount] = snapshot.wrongCount
                row[UserVocabularyProgress.lastReviewedAt] = times.lastReviewedAt
                row[UserVocabularyProgress.nextReviewAt] = times.nextReviewAt
            }
            if (isNewerClientState(existingClientUpdatedAt, times.clientUpdatedAt)) {
                row[UserVocabularyProgress.clientUpdatedAt] = times.clientUpdatedAt
            }
            row[UserVocabularyProgress.updatedAt] = times.now
        }
    }

    private fun isReviewSnapshotOlder(
        existing: ResultRow,
        snapshot: VocabularyProgressSnapshotDto,
        lastReviewedAt: LocalDateTime?,
    ): Boolean {
        val existingLastReviewedAt = existing[UserVocabularyProgress.lastReviewedAt]
        return snapshot.reviewCount < existing[UserVocabularyProgress.reviewCount] ||
            (
                snapshot.reviewCount == existing[UserVocabularyProgress.reviewCount] &&
                    existingLastReviewedAt != null &&
                    (lastReviewedAt == null || !lastReviewedAt.isAfter(existingLastReviewedAt))
            )
    }

    private fun isClientStateOlder(
        existing: LocalDateTime?,
        incoming: LocalDateTime?,
    ): Boolean = existing != null && (incoming == null || !incoming.isAfter(existing))

    private fun isNewerClientState(
        existing: LocalDateTime?,
        incoming: LocalDateTime?,
    ): Boolean = incoming != null && (existing == null || incoming.isAfter(existing))

    private fun shouldIgnoreSnapshot(
        eventType: String,
        reviewIsOlder: Boolean,
        favoriteIsOlder: Boolean,
    ): Boolean =
        (eventType == ProgressEventTypes.VOCABULARY_STATE_SYNC && favoriteIsOlder) ||
            (eventType != ProgressEventTypes.VOCABULARY_STATE_SYNC && reviewIsOlder)

    private fun requireVocabulary(vocabularyId: Long?): Long {
        val id = vocabularyId ?: throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.VALIDATION, "refId is required")
        val exists = Vocabularies.selectAll().andWhere { Vocabularies.id eq id }.any()
        if (!exists) {
            throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "Vocabulary not found")
        }
        return id
    }

    private fun toDatabaseTime(epochMillis: Long?): LocalDateTime? =
        epochMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }

    private fun toEpochMillis(value: LocalDateTime?): Long? = value?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    fun vocabularyProgress(userId: Long): List<VocabularyProgressItemDto> =
        transaction {
            UserVocabularyProgress
                .selectAll()
                .andWhere { UserVocabularyProgress.userId eq userId }
                .orderBy(UserVocabularyProgress.vocabularyId, SortOrder.ASC)
                .map { row ->
                    VocabularyProgressItemDto(
                        vocabularyId = row[UserVocabularyProgress.vocabularyId],
                        favorite = row[UserVocabularyProgress.favorite],
                        masteryLevel = row[UserVocabularyProgress.masteryLevel],
                        reviewCount = row[UserVocabularyProgress.reviewCount],
                        correctCount = row[UserVocabularyProgress.correctCount],
                        wrongCount = row[UserVocabularyProgress.wrongCount],
                        lastReviewedAtEpochMillis = toEpochMillis(row[UserVocabularyProgress.lastReviewedAt]),
                        nextReviewAtEpochMillis = toEpochMillis(row[UserVocabularyProgress.nextReviewAt]),
                        stateUpdatedAtEpochMillis = toEpochMillis(row[UserVocabularyProgress.clientUpdatedAt]),
                    )
                }
        }

    /**
     * Adds activity minutes to today's row, creating it on first activity.
     * Select-then-insert-or-update rather than an upsert so the unique index
     * (`uq_streak_user_date`) is honoured without relying on dialect-specific
     * upsert support.
     */
    private fun creditActivityDay(
        userId: Long,
        day: LocalDate,
        minutes: Int,
    ) {
        val row =
            LearningStreaks
                .selectAll()
                .andWhere { LearningStreaks.userId eq userId }
                .andWhere { LearningStreaks.activityDate eq day }
                .firstOrNull()

        if (row == null) {
            try {
                LearningStreaks.insert { insert ->
                    insert[LearningStreaks.userId] = userId
                    insert[LearningStreaks.activityDate] = day
                    insert[LearningStreaks.minutes] = minutes
                }
            } catch (duplicate: ExposedSQLException) {
                // A concurrent event created today's row first: fall through
                // to the update branch instead of failing the event.
                if (!duplicate.isDuplicateKey()) throw duplicate
                LearningStreaks.update(
                    { (LearningStreaks.userId eq userId) and (LearningStreaks.activityDate eq day) },
                ) {
                    with(SqlExpressionBuilder) {
                        it[LearningStreaks.minutes] = LearningStreaks.minutes + minutes
                    }
                }
            }
        } else if (minutes > 0) {
            val id = row[LearningStreaks.id]
            val current = row[LearningStreaks.minutes]
            LearningStreaks.update({ LearningStreaks.id eq id }) {
                it[LearningStreaks.minutes] = current + minutes
            }
        }
    }

    fun summary(userId: Long): ProgressSummaryDto =
        transaction {
            val activityRows =
                LearningStreaks
                    .selectAll()
                    .andWhere { LearningStreaks.userId eq userId }
                    .andWhere { LearningStreaks.activityDate greaterEq LocalDate.now().minusDays(streakWindowDays) }
                    .orderBy(LearningStreaks.activityDate, SortOrder.DESC)
                    .map { it[LearningStreaks.activityDate] to it[LearningStreaks.minutes] }

            val minutesByDay = activityRows.toMap()
            val activityDays = activityRows.map { it.first }.toSet()

            val quizAttempts =
                QuizAttempts
                    .selectAll()
                    .andWhere { QuizAttempts.userId eq userId }
                    .map { it[QuizAttempts.score] to it[QuizAttempts.total] }

            val aiConversations =
                AiConversations
                    .selectAll()
                    .andWhere { AiConversations.userId eq userId }
                    .count()

            val vocabulary =
                UserVocabularyProgress
                    .selectAll()
                    .andWhere { UserVocabularyProgress.userId eq userId }
                    .map {
                        Triple(
                            it[UserVocabularyProgress.masteryLevel],
                            it[UserVocabularyProgress.reviewCount],
                            it[UserVocabularyProgress.nextReviewAt],
                        )
                    }

            val now = LocalDateTime.now()
            val weakTopics =
                UserMistakes
                    .selectAll()
                    .andWhere { UserMistakes.userId eq userId }
                    .andWhere { UserMistakes.resolved eq false }
                    .map { it[UserMistakes.topic] }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(weakTopicLimit)
                    .map { WeakTopicDto(topic = it.key, occurrences = it.value) }

            val activityStrip =
                (activityWindowDays - 1 downTo 0).map { offset ->
                    val day = LocalDate.now().minusDays(offset.toLong())
                    ActivityDayDto(date = day.toString(), minutes = minutesByDay[day] ?: 0)
                }

            ProgressSummaryDto(
                streak = computeStreak(activityDays),
                totals =
                    ProgressTotalsDto(
                        // Int-typed DTO: clamp the Long sum before narrowing.
                        minutesStudied = activityRows.sumOf { it.second.toLong() }.coerceAtMost(maxIntAsLong).toInt(),
                        activeDays = activityDays.size,
                        quizAttempts = quizAttempts.size,
                        quizAverageScore =
                            quizAttempts
                                .filter { it.second > 0 }
                                .takeIf { it.isNotEmpty() }
                                ?.let { attempts -> attempts.sumOf { it.first.toDouble() / it.second } / attempts.size },
                        aiConversations = aiConversations.toInt(),
                    ),
                vocabulary =
                    VocabularyProgressDto(
                        tracked = vocabulary.size,
                        mastered = vocabulary.count { it.first >= masteryLearnedFloor },
                        learning = vocabulary.count { it.first in learningBand },
                        fresh = vocabulary.count { it.first <= 0 },
                        dueForReview = vocabulary.count { it.third != null && it.third!! <= now },
                    ),
                recentActivity = activityStrip,
                weakTopics = weakTopics,
            )
        }

    /**
     * Current streak counts back from today, or from yesterday when the learner
     * has not studied yet today — an unstarted day must not read as a broken
     * streak.
     */
    private fun computeStreak(activityDays: Set<LocalDate>): StreakDto {
        if (activityDays.isEmpty()) {
            return StreakDto(current = 0, longest = 0, lastActiveDate = null)
        }

        val today = LocalDate.now()
        val anchor =
            when {
                activityDays.contains(today) -> today
                activityDays.contains(today.minusDays(1)) -> today.minusDays(1)
                else -> null
            }

        var current = 0
        if (anchor != null) {
            var day: LocalDate = anchor
            while (activityDays.contains(day)) {
                current++
                day = day.minusDays(1)
            }
        }

        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in activityDays.sorted()) {
            val prior = previous
            run = if (prior != null && day == prior.plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
            previous = day
        }

        return StreakDto(
            current = current,
            longest = longest,
            lastActiveDate = activityDays.maxOrNull()?.toString(),
        )
    }
}

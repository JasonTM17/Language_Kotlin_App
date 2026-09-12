package com.linguaai.server.repository

import com.linguaai.server.api.dto.ActivityDayDto
import com.linguaai.server.api.dto.ProgressEventTypes
import com.linguaai.server.api.dto.ProgressSummaryDto
import com.linguaai.server.api.dto.ProgressTotalsDto
import com.linguaai.server.api.dto.RecordProgressEventRequest
import com.linguaai.server.api.dto.RecordProgressEventResponse
import com.linguaai.server.api.dto.StreakDto
import com.linguaai.server.api.dto.VocabularyProgressDto
import com.linguaai.server.api.dto.WeakTopicDto
import com.linguaai.server.db.AiConversations
import com.linguaai.server.db.LearningStreaks
import com.linguaai.server.db.QuizAttempts
import com.linguaai.server.db.UserMistakes
import com.linguaai.server.db.UserProgress
import com.linguaai.server.db.UserVocabularyProgress
import java.time.LocalDate
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

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

    // Mastery bands on the shared 0..5 scale. The Android SRS scheduler uses the
    // same scale, so a change here has to be mirrored there — which is only
    // possible if the numbers are findable.
    private val masteryLearnedFloor = 4
    private val learningBand = 1..3

    /**
     * Records one learning event. Only [ProgressEventTypes.all] count toward a
     * streak; anything else is stored but does not extend activity.
     */
    fun recordEvent(userId: Long, request: RecordProgressEventRequest): RecordProgressEventResponse =
        transaction {
            val existing = UserProgress.selectAll()
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
            val minutes = if (isMeaningful) request.minutes.coerceAtLeast(0) else 0

            val eventId = UserProgress.insert { row ->
                row[UserProgress.userId] = userId
        row[UserProgress.clientOperationId] = request.clientOperationId.take(maxOperationIdLength)
        row[UserProgress.eventType] = request.eventType.take(maxEventTypeLength)
                row[UserProgress.refId] = request.refId
                row[UserProgress.minutes] = minutes
                row[UserProgress.occurredAt] = now
                row[UserProgress.createdAt] = now
            } get UserProgress.id

            if (isMeaningful) {
                creditActivityDay(userId, LocalDate.now(), minutes)
            }

            RecordProgressEventResponse(eventId = eventId, created = true)
        }

    /**
     * Adds activity minutes to today's row, creating it on first activity.
     * Select-then-insert-or-update rather than an upsert so the unique index
     * (`uq_streak_user_date`) is honoured without relying on dialect-specific
     * upsert support.
     */
    private fun creditActivityDay(userId: Long, day: LocalDate, minutes: Int) {
        val row = LearningStreaks.selectAll()
            .andWhere { LearningStreaks.userId eq userId }
            .andWhere { LearningStreaks.activityDate eq day }
            .firstOrNull()

        if (row == null) {
            LearningStreaks.insert { insert ->
                insert[LearningStreaks.userId] = userId
                insert[LearningStreaks.activityDate] = day
                insert[LearningStreaks.minutes] = minutes
            }
        } else if (minutes > 0) {
            val id = row[LearningStreaks.id]
            val current = row[LearningStreaks.minutes]
            LearningStreaks.update({ LearningStreaks.id eq id }) {
                it[LearningStreaks.minutes] = current + minutes
            }
        }
    }

    fun summary(userId: Long): ProgressSummaryDto = transaction {
        val activityRows = LearningStreaks.selectAll()
            .andWhere { LearningStreaks.userId eq userId }
            .andWhere { LearningStreaks.activityDate greaterEq LocalDate.now().minusDays(streakWindowDays) }
            .orderBy(LearningStreaks.activityDate, SortOrder.DESC)
            .map { it[LearningStreaks.activityDate] to it[LearningStreaks.minutes] }

        val minutesByDay = activityRows.toMap()
        val activityDays = activityRows.map { it.first }.toSet()

        val quizAttempts = QuizAttempts.selectAll()
            .andWhere { QuizAttempts.userId eq userId }
            .map { it[QuizAttempts.score] to it[QuizAttempts.total] }

        val aiConversations = AiConversations.selectAll()
            .andWhere { AiConversations.userId eq userId }
            .count()

        val vocabulary = UserVocabularyProgress.selectAll()
            .andWhere { UserVocabularyProgress.userId eq userId }
            .map {
                Triple(
                    it[UserVocabularyProgress.masteryLevel],
                    it[UserVocabularyProgress.reviewCount],
                    it[UserVocabularyProgress.nextReviewAt],
                )
            }

        val now = LocalDateTime.now()
        val weakTopics = UserMistakes.selectAll()
            .andWhere { UserMistakes.userId eq userId }
            .andWhere { UserMistakes.resolved eq false }
            .map { it[UserMistakes.topic] }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(weakTopicLimit)
            .map { WeakTopicDto(topic = it.key, occurrences = it.value) }

        val activityStrip = (activityWindowDays - 1 downTo 0).map { offset ->
            val day = LocalDate.now().minusDays(offset.toLong())
            ActivityDayDto(date = day.toString(), minutes = minutesByDay[day] ?: 0)
        }

        ProgressSummaryDto(
            streak = computeStreak(activityDays),
            totals = ProgressTotalsDto(
                minutesStudied = activityRows.sumOf { it.second },
                activeDays = activityDays.size,
                quizAttempts = quizAttempts.size,
                quizAverageScore = quizAttempts
                    .filter { it.second > 0 }
                    .takeIf { it.isNotEmpty() }
                    ?.let { attempts -> attempts.sumOf { it.first.toDouble() / it.second } / attempts.size },
                aiConversations = aiConversations.toInt(),
            ),
            vocabulary = VocabularyProgressDto(
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
        val anchor = when {
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

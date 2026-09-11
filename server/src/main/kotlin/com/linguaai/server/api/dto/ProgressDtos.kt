package com.linguaai.server.api.dto

import kotlinx.serialization.Serializable

/**
 * Progress surface for the learner dashboard.
 *
 * The server is the single source of truth for streak and totals; the client
 * only renders what it receives. Streak is derived from recorded activity days
 * rather than a stored counter, so a device clock change cannot inflate it.
 */
@Serializable
data class ProgressSummaryDto(
    val streak: StreakDto,
    val totals: ProgressTotalsDto,
    val vocabulary: VocabularyProgressDto,
    val recentActivity: List<ActivityDayDto>,
    val weakTopics: List<WeakTopicDto>,
)

@Serializable
data class StreakDto(
    /** Consecutive days ending today or yesterday. */
    val current: Int,
    val longest: Int,
    /** ISO-8601 date of the most recent activity day, or null if none. */
    val lastActiveDate: String?,
)

@Serializable
data class ProgressTotalsDto(
    val minutesStudied: Int,
    val activeDays: Int,
    val quizAttempts: Int,
    /** Average quiz score as a fraction in 0..1, or null when no attempt exists. */
    val quizAverageScore: Double? = null,
    val aiConversations: Int,
)

@Serializable
data class VocabularyProgressDto(
    val tracked: Int,
    val mastered: Int,
    val learning: Int,
    val fresh: Int,
    val dueForReview: Int,
)

@Serializable
data class ActivityDayDto(
    val date: String,
    val minutes: Int,
)

@Serializable
data class WeakTopicDto(
    val topic: String,
    val occurrences: Int,
)

/**
 * A meaningful learning event. Recorded idempotently: replaying the same
 * `clientOperationId` returns the existing row instead of creating a second one,
 * which is what makes offline retry/backoff safe.
 */
@Serializable
data class RecordProgressEventRequest(
    val clientOperationId: String,
    val eventType: String,
    val refId: Long? = null,
    val minutes: Int = 0,
)

@Serializable
data class RecordProgressEventResponse(
    val eventId: Long,
    /** False when this call replayed an operation that was already recorded. */
    val created: Boolean,
)

/** Event types that count toward a streak. Keep in sync with the client. */
object ProgressEventTypes {
    const val FLASHCARD_REVIEW = "FLASHCARD_REVIEW"
    const val QUIZ_ATTEMPT = "QUIZ_ATTEMPT"
    const val AI_CORRECTION = "AI_CORRECTION"
    const val LESSON_COMPLETED = "LESSON_COMPLETED"

    val all = setOf(FLASHCARD_REVIEW, QUIZ_ATTEMPT, AI_CORRECTION, LESSON_COMPLETED)
}

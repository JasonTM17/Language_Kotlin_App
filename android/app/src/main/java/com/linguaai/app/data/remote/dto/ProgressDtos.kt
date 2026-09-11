package com.linguaai.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Progress payloads mirroring the server contract.
 *
 * The client never derives the streak — it renders what the server returns. These
 * types are also the serialized form stored in `progress_cache`, so changing a
 * field name here is a cache-format change.
 */
@Serializable
data class ProgressSummaryDto(
    val streak: StreakDto,
    val totals: ProgressTotalsDto,
    val vocabulary: VocabularyProgressDto,
    val recentActivity: List<ActivityDayDto> = emptyList(),
    val weakTopics: List<WeakTopicDto> = emptyList(),
)

@Serializable
data class StreakDto(
    val current: Int,
    val longest: Int,
    val lastActiveDate: String? = null,
)

@Serializable
data class ProgressTotalsDto(
    val minutesStudied: Int,
    val activeDays: Int,
    val quizAttempts: Int,
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

/** Request body for recording a learning event. Idempotent per operation id. */
@Serializable
data class RecordProgressEventRequestDto(
    val clientOperationId: String,
    val eventType: String,
    val refId: Long? = null,
    val minutes: Int = 0,
)

@Serializable
data class RecordProgressEventResponseDto(
    val eventId: Long,
    val created: Boolean,
)

/** Event types that count toward a streak. Keep in sync with the server. */
object ProgressEventTypes {
    const val FLASHCARD_REVIEW = "FLASHCARD_REVIEW"
    const val QUIZ_ATTEMPT = "QUIZ_ATTEMPT"
    const val AI_CORRECTION = "AI_CORRECTION"
    const val LESSON_COMPLETED = "LESSON_COMPLETED"
}

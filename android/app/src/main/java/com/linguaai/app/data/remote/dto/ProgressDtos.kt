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
    val vocabularyProgress: VocabularyProgressSnapshotDto? = null,
)

/** Snapshot of the local SRS state attached to a flashcard review event. */
@Serializable
data class VocabularyProgressSnapshotDto(
    val favorite: Boolean = false,
    val masteryLevel: Int = 0,
    val reviewCount: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastReviewedAtEpochMillis: Long? = null,
    val nextReviewAtEpochMillis: Long? = null,
    val stateUpdatedAtEpochMillis: Long? = null,
)

/** Per-word state returned when a device hydrates its local vocabulary cache. */
@Serializable
data class VocabularyProgressItemDto(
    val vocabularyId: Long,
    val favorite: Boolean,
    val masteryLevel: Int,
    val reviewCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val lastReviewedAtEpochMillis: Long? = null,
    val nextReviewAtEpochMillis: Long? = null,
    val stateUpdatedAtEpochMillis: Long? = null,
)

@Serializable
data class RecordProgressEventResponseDto(
    val eventId: Long,
    val created: Boolean,
)

/** Event types that count toward a streak. Keep in sync with the server. */
object ProgressEventTypes {
    const val FLASHCARD_REVIEW = "FLASHCARD_REVIEW"
    const val VOCABULARY_STATE_SYNC = "VOCABULARY_STATE_SYNC"
    const val QUIZ_ATTEMPT = "QUIZ_ATTEMPT"
    const val AI_CORRECTION = "AI_CORRECTION"
    const val LESSON_COMPLETED = "LESSON_COMPLETED"
}

package com.linguaai.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val id: Long,
    val languageId: Long,
    val level: String,
    val title: String,
    val description: String?,
    val type: String,
    val estimatedMinutes: Int,
    val difficulty: Int,
    val content: String?,
    val cachedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "vocabulary")
data class VocabularyEntity(
    @PrimaryKey val id: Long,
    val languageId: Long,
    val level: String,
    val word: String,
    val reading: String?,
    val pronunciation: String?,
    val meaning: String,
    val example: String?,
    val exampleTranslation: String?,
    val category: String?,
    val favorite: Boolean = false,
    val masteryLevel: Int = 0,
    val reviewCount: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long? = null,
    val stateUpdatedAt: Long? = null,
    val cachedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "grammar_lessons")
data class GrammarEntity(
    @PrimaryKey val id: Long,
    val languageId: Long,
    val level: String,
    val title: String,
    val structure: String?,
    val meaning: String?,
    val usage: String?,
    val examplesJson: String?,
    val notes: String?,
    val difficulty: Int,
    val cachedAt: Long = System.currentTimeMillis(),
)

/** Outbox entry for idempotent background sync of learning events. */
@Entity(tableName = "pending_sync_ops", indices = [Index(value = ["operationId"], unique = true)])
data class PendingSyncOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationId: String,
    val eventType: String,
    val refId: Long?,
    val minutes: Int,
    val occurredAt: Long,
    val state: String = SyncOpState.STATE_PENDING,
    // Declared with a SQL default so the v3 -> v4 ALTER TABLE matches the schema
    // Room expects. Room compares column defaults, so an ALTER that adds
    // DEFAULT 0 without this annotation would fail migration validation.
    @ColumnInfo(defaultValue = "0") val attempts: Int = 0,
    /** Optional SRS snapshot; populated only for flashcard review events. */
    val vocabularyFavorite: Boolean? = null,
    val vocabularyMasteryLevel: Int? = null,
    val vocabularyReviewCount: Int? = null,
    val vocabularyCorrectCount: Int? = null,
    val vocabularyWrongCount: Int? = null,
    val vocabularyLastReviewedAt: Long? = null,
    val vocabularyNextReviewAt: Long? = null,
    val vocabularyStateUpdatedAt: Long? = null,
)

object SyncOpState {
    const val STATE_PENDING = "PENDING"
    const val STATE_SYNCING = "SYNCING"
    const val STATE_SYNCED = "SYNCED"
    const val STATE_FAILED = "FAILED"
}

/** Offline cache of AI conversation messages (server stays source of truth). */
@Entity(tableName = "ai_message_cache", indices = [Index(value = ["conversationId"])])
data class AiMessageCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val cachedAt: Long = System.currentTimeMillis(),
)

/**
 * Single-row cache of the last progress summary the server returned.
 *
 * The summary is a read-only server aggregate, so it is cached as its serialized
 * payload rather than being shredded into relational tables. `id` is pinned to
 * [SINGLETON_ID] so there is never more than one row.
 */
@Entity(tableName = "progress_cache")
data class ProgressCacheEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val payload: String,
    val cachedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

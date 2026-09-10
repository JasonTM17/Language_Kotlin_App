package com.linguaai.app.data.local.entity

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
)

object SyncOpState {
    const val STATE_PENDING = "PENDING"
    const val STATE_SYNCING = "SYNCING"
    const val STATE_SYNCED = "SYNCED"
    const val STATE_FAILED = "FAILED"
}

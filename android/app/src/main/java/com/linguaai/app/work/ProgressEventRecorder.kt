package com.linguaai.app.work

import android.content.Context
import androidx.room.withTransaction
import com.linguaai.app.data.local.LinguaDatabase
import com.linguaai.app.data.local.dao.SyncDao
import com.linguaai.app.data.local.entity.PendingSyncOpEntity
import com.linguaai.app.data.remote.dto.VocabularyProgressSnapshotDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entry point for recording a learning event.
 *
 * Events are written to the outbox first and delivered later, so recording never
 * depends on connectivity and never blocks the UI. The `operationId` is minted
 * **here, once, at event creation** — not at delivery time. That is what makes
 * retries idempotent: every retry of the same event carries the same id, so the
 * server recognises it as a duplicate instead of counting it twice.
 */
@Singleton
class ProgressEventRecorder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: LinguaDatabase,
        private val syncDao: SyncDao,
    ) {
        suspend fun record(
            eventType: String,
            refId: Long? = null,
            minutes: Int = 0,
            vocabularyProgress: VocabularyProgressSnapshotDto? = null,
            localUpdate: (suspend () -> Unit)? = null,
        ) {
            database.withTransaction {
                localUpdate?.invoke()
                val now = System.currentTimeMillis()
                syncDao.enqueue(
                    PendingSyncOpEntity(
                        operationId = UUID.randomUUID().toString(),
                        eventType = eventType,
                        refId = refId,
                        minutes = minutes,
                        occurredAt = now,
                        vocabularyFavorite = vocabularyProgress?.favorite,
                        vocabularyMasteryLevel = vocabularyProgress?.masteryLevel,
                        vocabularyReviewCount = vocabularyProgress?.reviewCount,
                        vocabularyCorrectCount = vocabularyProgress?.correctCount,
                        vocabularyWrongCount = vocabularyProgress?.wrongCount,
                        vocabularyLastReviewedAt = vocabularyProgress?.lastReviewedAtEpochMillis,
                        vocabularyNextReviewAt = vocabularyProgress?.nextReviewAtEpochMillis,
                        vocabularyStateUpdatedAt = vocabularyProgress?.stateUpdatedAtEpochMillis,
                    ),
                )
            }
            // Kick the drain now; WorkManager coalesces, so a burst of events
            // results in one sync run rather than one per event.
            WorkScheduler.enqueueSync(context)
        }
    }

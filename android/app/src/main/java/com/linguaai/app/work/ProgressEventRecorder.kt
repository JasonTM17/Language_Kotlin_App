package com.linguaai.app.work

import android.content.Context
import com.linguaai.app.data.local.dao.SyncDao
import com.linguaai.app.data.local.entity.PendingSyncOpEntity
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
class ProgressEventRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncDao: SyncDao,
) {

    suspend fun record(
        eventType: String,
        refId: Long? = null,
        minutes: Int = 0,
    ) {
        syncDao.enqueue(
            PendingSyncOpEntity(
                operationId = UUID.randomUUID().toString(),
                eventType = eventType,
                refId = refId,
                minutes = minutes,
                occurredAt = System.currentTimeMillis(),
            ),
        )
        // Kick the drain now; WorkManager coalesces, so a burst of events
        // results in one sync run rather than one per event.
        WorkScheduler.enqueueSync(context)
    }
}

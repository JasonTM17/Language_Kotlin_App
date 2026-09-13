package com.linguaai.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.linguaai.app.data.local.dao.SyncDao
import com.linguaai.app.data.local.entity.PendingSyncOpEntity
import com.linguaai.app.data.local.entity.SyncOpState
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.ProgressRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Drains the outbox of recorded learning events to the server.
 *
 * Safety rests on the server's idempotency contract: every operation carries a
 * stable `operationId` and the server keys on it, so re-delivering an operation
 * that actually succeeded on a previous attempt (but whose response was lost)
 * is a no-op rather than a double count. That is why this worker can treat a
 * duplicate response as success and why retrying is safe at all.
 */
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val syncDao: SyncDao,
        private val progressRepository: ProgressRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            // Recover ops stranded in SYNCING by a previous process death before
            // selecting the batch; re-delivering them is a server-side no-op.
            syncDao.recoverStuckSyncing()
            val batch = syncDao.pending(BATCH_SIZE)
            if (batch.isEmpty()) {
                syncDao.deleteByState(SyncOpState.STATE_SYNCED)
                return Result.success()
            }

            var failures = 0
            for (op in batch) {
                syncDao.updateState(op.id, SyncOpState.STATE_SYNCING)

                when (deliver(op)) {
                    is AppResult.Success -> syncDao.updateState(op.id, SyncOpState.STATE_SYNCED)
                    is AppResult.Failure -> {
                        failures++
                        onDeliveryFailed(op)
                    }
                }
            }

            // Housekeeping: synced rows have served their purpose once the batch is done.
            syncDao.deleteByState(SyncOpState.STATE_SYNCED)

            return if (failures == 0) {
                Result.success()
            } else {
                // WorkManager's exponential backoff paces the retry; ops that keep
                // failing are dead-lettered in onDeliveryFailed.
                Result.retry()
            }
        }

        private suspend fun deliver(op: PendingSyncOpEntity): AppResult<Unit> =
            progressRepository.recordEvent(
                operationId = op.operationId,
                eventType = op.eventType,
                refId = op.refId,
                minutes = op.minutes,
            )

        /**
         * Increments the attempt counter and parks the op. Once [MAX_ATTEMPTS] is
         * exceeded the op is left FAILED so it stops being retried forever — a
         * poison message must not keep the whole queue retrying indefinitely.
         */
        private suspend fun onDeliveryFailed(op: PendingSyncOpEntity) {
            val nextAttempts = op.attempts + 1
            val nextState =
                if (nextAttempts >= MAX_ATTEMPTS) {
                    Timber.w("Sync op %s dead-lettered after %d attempts", op.operationId, nextAttempts)
                    SyncOpState.STATE_FAILED
                } else {
                    SyncOpState.STATE_PENDING
                }
            syncDao.recordAttempt(op.id, nextState)
        }

        companion object {
            const val BATCH_SIZE = 50
            const val MAX_ATTEMPTS = 5
        }
    }

package com.linguaai.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Owns every piece of background scheduling.
 *
 * All work uses a unique name so repeated calls coalesce instead of stacking
 * duplicate requests — enqueueing sync on every app start must not build a queue
 * of identical workers.
 */
object WorkScheduler {
    private const val SYNC_WORK_NAME = "linguaai-progress-sync"
    private const val REMINDER_WORK_NAME = "linguaai-study-reminder"

    /** First retry delay; WorkManager's exponential policy grows it from here. */
    private const val SYNC_BACKOFF_SECONDS = 30L

    /** A daily reminder repeats once every 24 hours. */
    private const val REMINDER_INTERVAL_HOURS = 24L

    /** Queues an outbox drain. Safe to call often; the unique name coalesces. */
    fun enqueueSync(
        context: Context,
        replaceExisting: Boolean = false,
    ) {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    SYNC_BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                ).build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Schedules the daily reminder at [hour]:[minute] local time.
     *
     * A periodic request with an initial delay to the next occurrence is used
     * rather than a self-rescheduling one-shot: the reminder must still fire on
     * days the app is never opened. The trade-off is that WorkManager may drift
     * by a few minutes, which is acceptable for a study nudge.
     */
    fun scheduleReminder(
        context: Context,
        hour: Int,
        minute: Int,
    ) {
        val initialDelay =
            Duration
                .between(
                    LocalDateTime.now(),
                    nextOccurrence(LocalTime.of(hour, minute)),
                ).coerceAtLeast(Duration.ofMinutes(1))

        val request =
            PeriodicWorkRequestBuilder<ReminderWorker>(REMINDER_INTERVAL_HOURS, TimeUnit.HOURS)
                .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(SYNC_WORK_NAME)
            cancelUniqueWork(REMINDER_WORK_NAME)
        }
    }

    /** Next wall-clock occurrence of [time], today if it is still ahead. */
    internal fun nextOccurrence(
        time: LocalTime,
        now: LocalDateTime = LocalDateTime.now(),
    ): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}

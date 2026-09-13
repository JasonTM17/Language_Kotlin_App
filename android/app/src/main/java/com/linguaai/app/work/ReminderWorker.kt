package com.linguaai.app.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.linguaai.app.MainActivity
import com.linguaai.app.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Posts the daily study reminder.
 *
 * Returns success even when the notification cannot be shown (permission
 * revoked, channel disabled): that is a user choice, not a transient failure, so
 * retrying would be wrong. Returning failure here would make WorkManager retry
 * forever against a decision the user already made.
 */
@HiltWorker
class ReminderWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted params: WorkerParameters,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!hasNotificationPermission()) return Result.success()

            val intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notification =
                NotificationCompat
                    .Builder(context, NotificationChannels.STUDY_REMINDERS)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Time to practise")
                    .setContentText("A few minutes today keeps your streak alive.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

            return runCatching {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
                Result.success()
            }.getOrElse {
                // Missing POST_NOTIFICATIONS on API 33+ throws SecurityException.
                Result.success()
            }
        }

        private fun hasNotificationPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        companion object {
            private const val NOTIFICATION_ID = 1001
            private const val REQUEST_CODE = 2001
        }
    }

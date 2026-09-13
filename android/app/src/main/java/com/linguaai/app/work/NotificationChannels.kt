package com.linguaai.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Notification channels created once at app start.
 *
 * Creating them eagerly (rather than on first post) avoids the trap where a
 * notification is silently dropped because its channel did not exist yet.
 */
object NotificationChannels {
    const val STUDY_REMINDERS = "study_reminders"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val reminders =
            NotificationChannel(
                STUDY_REMINDERS,
                "Study reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Daily nudges to keep your learning streak going"
            }

        manager.createNotificationChannel(reminders)
    }
}

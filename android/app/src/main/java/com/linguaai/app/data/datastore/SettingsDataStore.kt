package com.linguaai.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App-level settings (theme, daily goal, onboarding, notifications). */
@Singleton
class SettingsDataStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val themeMode: Flow<String> = context.settingsDataStore.data.map { it[THEME_MODE] ?: THEME_SYSTEM }
        val dailyGoalMinutes: Flow<Int> =
            context.settingsDataStore.data.map { it[DAILY_GOAL] ?: DEFAULT_DAILY_GOAL_MINUTES }
        val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { it[ONBOARDING_DONE] ?: false }
        val learningLanguageId: Flow<Long?> = context.settingsDataStore.data.map { it[LEARNING_LANGUAGE_ID] }
        val notificationsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[NOTIFICATIONS] ?: true }
        val reminderHour: Flow<Int> = context.settingsDataStore.data.map { it[REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR }
        val reminderMinute: Flow<Int> = context.settingsDataStore.data.map { it[REMINDER_MINUTE] ?: 0 }

        suspend fun setThemeMode(mode: String) {
            context.settingsDataStore.edit { it[THEME_MODE] = mode }
        }

        suspend fun setDailyGoalMinutes(minutes: Int) {
            context.settingsDataStore.edit { it[DAILY_GOAL] = minutes }
        }

        suspend fun setOnboardingCompleted() {
            context.settingsDataStore.edit { it[ONBOARDING_DONE] = true }
        }

        /** Keeps content screens scoped while the profile endpoint is offline. */
        suspend fun setLearningLanguageId(languageId: Long?) {
            context.settingsDataStore.edit { preferences ->
                if (languageId == null) {
                    preferences.remove(LEARNING_LANGUAGE_ID)
                } else {
                    preferences[LEARNING_LANGUAGE_ID] = languageId
                }
            }
        }

        suspend fun setNotificationsEnabled(enabled: Boolean) {
            context.settingsDataStore.edit { it[NOTIFICATIONS] = enabled }
        }

        suspend fun setReminderTime(
            hour: Int,
            minute: Int,
        ) {
            context.settingsDataStore.edit {
                it[REMINDER_HOUR] = hour.coerceIn(FIRST_HOUR, LAST_HOUR)
                it[REMINDER_MINUTE] = minute.coerceIn(FIRST_MINUTE, LAST_MINUTE)
            }
        }

        companion object {
            const val THEME_SYSTEM = "system"
            const val THEME_LIGHT = "light"
            const val THEME_DARK = "dark"

            /** 19:00 — an evening default that suits a study reminder. */
            const val DEFAULT_REMINDER_HOUR = 19

            /** Used until the learner picks a daily goal during onboarding. */
            private const val DEFAULT_DAILY_GOAL_MINUTES = 20

            /** The bounds of a wall clock, so a stored reminder time is always valid. */
            private const val FIRST_HOUR = 0
            private const val LAST_HOUR = 23
            private const val FIRST_MINUTE = 0
            private const val LAST_MINUTE = 59

            private val THEME_MODE = stringPreferencesKey("theme_mode")
            private val DAILY_GOAL = intPreferencesKey("daily_goal_minutes")
            private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
            private val LEARNING_LANGUAGE_ID = longPreferencesKey("learning_language_id")
            private val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
            private val REMINDER_HOUR = intPreferencesKey("reminder_hour")
            private val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        }
    }

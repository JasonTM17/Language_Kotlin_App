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
import java.time.LocalDate
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

        /** The last five quiz percentages, newest first. */
        val quizScoreHistory: Flow<List<QuizScoreEntry>> =
            context.settingsDataStore.data.map { prefs -> parseQuizScoreHistory(prefs[QUIZ_SCORES]) }

        /** Appends one percentage to the history, newest first, capped at five entries. */
        suspend fun recordQuizScore(percent: Int) {
            context.settingsDataStore.edit { prefs ->
                val history = parseQuizScoreHistory(prefs[QUIZ_SCORES])
                val updated = (listOf(QuizScoreEntry(percent, epochDay())) + history).take(QUIZ_HISTORY_LIMIT)
                prefs[QUIZ_SCORES] = updated.joinToString(",") { "${it.percent}|${it.epochDay}" }
            }
        }

        val learningLanguageId: Flow<Long?> = context.settingsDataStore.data.map { it[LEARNING_LANGUAGE_ID] }

        /** The last few distinct vocabulary searches, newest first. */
        val recentVocabQueries: Flow<List<String>> =
            context.settingsDataStore.data.map { prefs ->
                parseRecentQueries(prefs[RECENT_QUERIES])
            }

        private fun parseRecentQueries(raw: String?): List<String> = raw.orEmpty().split(SEPARATOR_NEWLINE).filter { it.isNotBlank() }

        /** Remembers one search term, deduplicated, newest first, capped at five. */
        suspend fun rememberVocabQuery(query: String) {
            val term = query.trim()
            if (term.length < MIN_QUERY_LENGTH) return
            context.settingsDataStore.edit { prefs ->
                val updated =
                    (listOf(term) + parseRecentQueries(prefs[RECENT_QUERIES]))
                        .distinctBy { it.lowercase() }
                        .take(RECENT_QUERY_LIMIT)
                prefs[RECENT_QUERIES] = updated.joinToString(SEPARATOR_NEWLINE)
            }
        }

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
            private val QUIZ_SCORES = stringPreferencesKey("quiz_score_history")
            private const val QUIZ_HISTORY_LIMIT = 5
            private val RECENT_QUERIES = stringPreferencesKey("recent_vocab_queries")
            private const val RECENT_QUERY_LIMIT = 5
            private const val MIN_QUERY_LENGTH = 2
            private const val SEPARATOR_NEWLINE: String = "\n"
        }
    }

/** One stored quiz outcome. */
data class QuizScoreEntry(
    val percent: Int,
    val epochDay: Long,
)

private fun epochDay(): Long {
    val today = LocalDate.now()
    return today.toEpochDay()
}

private fun parseQuizScoreHistory(raw: String?): List<QuizScoreEntry> {
    val entries = raw.orEmpty().split(',')
    return entries.mapNotNull(::parseQuizScoreEntry)
}

private fun parseQuizScoreEntry(raw: String): QuizScoreEntry? {
    val parts = raw.split("|")
    val percent = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val day = parts.getOrNull(1)?.toLongOrNull() ?: return null
    return QuizScoreEntry(percent, day)
}

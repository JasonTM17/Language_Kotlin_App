package com.linguaai.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App-level settings (theme, daily goal, onboarding, notifications). */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val themeMode: Flow<String> = context.settingsDataStore.data.map { it[THEME_MODE] ?: THEME_SYSTEM }
    val dailyGoalMinutes: Flow<Int> = context.settingsDataStore.data.map { it[DAILY_GOAL] ?: 20 }
    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { it[ONBOARDING_DONE] ?: false }
    val notificationsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[NOTIFICATIONS] ?: true }

    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[DAILY_GOAL] = minutes }
    }

    suspend fun setOnboardingCompleted() {
        context.settingsDataStore.edit { it[ONBOARDING_DONE] = true }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[NOTIFICATIONS] = enabled }
    }

    private companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DAILY_GOAL = intPreferencesKey("daily_goal_minutes")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    }
}

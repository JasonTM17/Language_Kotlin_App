package com.linguaai.app.ui.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.remote.dto.UpdateProfileRequestDto
import com.linguaai.app.data.repository.ProfileData
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.data.session.SignOutCoordinator
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.ui.util.UiMessage
import com.linguaai.app.ui.util.toUiMessage
import com.linguaai.app.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: ProfileData? = null,
    val dailyGoalMinutes: Int = 20,
    val themeMode: String = SettingsDataStore.THEME_SYSTEM,
    val reminderHour: Int = SettingsDataStore.DEFAULT_REMINDER_HOUR,
    val reminderMinute: Int = 0,
    val notificationsEnabled: Boolean = true,
    val isSaving: Boolean = false,
    val error: UiMessage? = null,
    val signedOut: Boolean = false,
)

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val remoteAuthRepository: RemoteAuthRepository,
        private val settingsDataStore: SettingsDataStore,
        private val signOutCoordinator: SignOutCoordinator,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProfileUiState())
        val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        /** Public so a failed load is recoverable from the error screen. */
        fun load() {
            viewModelScope.launch {
                val theme = settingsDataStore.themeMode.first()
                val goal = settingsDataStore.dailyGoalMinutes.first()
                val hour = settingsDataStore.reminderHour.first()
                val minute = settingsDataStore.reminderMinute.first()
                val notifications = settingsDataStore.notificationsEnabled.first()

                _uiState.update {
                    it.copy(
                        themeMode = theme,
                        dailyGoalMinutes = goal,
                        reminderHour = hour,
                        reminderMinute = minute,
                        notificationsEnabled = notifications,
                    )
                }

                when (val result = remoteAuthRepository.fetchProfile()) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                profile = result.data,
                                dailyGoalMinutes = result.data.dailyGoalMinutes,
                            )
                        }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = result.error.toUiMessage())
                        }
                }
            }
        }

        /**
         * Daily goal is server-owned (it drives the home dashboard), so it is written
         * to both the local store and the profile endpoint. Local first so the UI is
         * responsive and survives an offline edit.
         */
        fun setDailyGoal(minutes: Int) {
            viewModelScope.launch {
                settingsDataStore.setDailyGoalMinutes(minutes)
                _uiState.update { it.copy(dailyGoalMinutes = minutes, isSaving = true, error = null) }
                when (
                    val result =
                        remoteAuthRepository.updateProfile(
                            UpdateProfileRequestDto(dailyGoalMinutes = minutes),
                        )
                ) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(isSaving = false, profile = result.data)
                        }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(isSaving = false, error = result.error.toUiMessage())
                        }
                }
            }
        }

        /** Theme is device-local only — never synced to the server. */
        fun setThemeMode(mode: String) {
            viewModelScope.launch {
                settingsDataStore.setThemeMode(mode)
                _uiState.update { it.copy(themeMode = mode) }
            }
        }

        /** Reminder time is device-local; changing it reschedules the worker. */
        fun setReminderTime(
            hour: Int,
            minute: Int,
        ) {
            viewModelScope.launch {
                settingsDataStore.setReminderTime(hour, minute)
                _uiState.update { it.copy(reminderHour = hour, reminderMinute = minute) }
                if (_uiState.value.notificationsEnabled) {
                    WorkScheduler.scheduleReminder(context, hour, minute)
                }
            }
        }

        /**
         * Disabling reminders cancels the scheduled work rather than only hiding the
         * notification: a disabled reminder that still runs is a battery and trust bug.
         */
        fun setNotificationsEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsDataStore.setNotificationsEnabled(enabled)
                _uiState.update { it.copy(notificationsEnabled = enabled) }
                if (enabled) {
                    WorkScheduler.scheduleReminder(
                        context,
                        _uiState.value.reminderHour,
                        _uiState.value.reminderMinute,
                    )
                } else {
                    WorkScheduler.cancelReminder(context)
                }
            }
        }

        fun signOut() {
            viewModelScope.launch {
                signOutCoordinator.signOut()
                _uiState.update { it.copy(signedOut = true) }
            }
        }
    }

package com.linguaai.app.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartDestination { LOGIN, ONBOARDING, HOME }

@HiltViewModel
class SplashViewModel
    @Inject
    constructor(
        private val remoteAuthRepository: RemoteAuthRepository,
        private val settingsDataStore: SettingsDataStore,
    ) : ViewModel() {
        private val _startDestination = MutableStateFlow<StartDestination?>(null)
        val startDestination: StateFlow<StartDestination?> = _startDestination.asStateFlow()

        init {
            resolveStartDestination()
        }

        fun resolveStartDestination() {
            viewModelScope.launch {
                val result = remoteAuthRepository.restoreSession()
                _startDestination.value =
                    when {
                        result is AppResult.Success -> {
                            val onboarded = settingsDataStore.onboardingCompleted.first() || result.data.onboarded
                            if (onboarded) StartDestination.HOME else StartDestination.ONBOARDING
                        }
                        else -> StartDestination.LOGIN
                    }
            }
        }
    }

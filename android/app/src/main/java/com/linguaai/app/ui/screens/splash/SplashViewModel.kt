package com.linguaai.app.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.repository.ProfileData
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartDestination { LOGIN, ONBOARDING, HOME }

internal fun startDestinationFor(result: AppResult<ProfileData>): StartDestination =
    when {
        result is AppResult.Success && result.data.onboarded -> StartDestination.HOME
        result is AppResult.Success -> StartDestination.ONBOARDING
        else -> StartDestination.LOGIN
    }

@HiltViewModel
class SplashViewModel
    @Inject
    constructor(
        private val remoteAuthRepository: RemoteAuthRepository,
    ) : ViewModel() {
        private val _startDestination = MutableStateFlow<StartDestination?>(null)
        val startDestination: StateFlow<StartDestination?> = _startDestination.asStateFlow()

        init {
            resolveStartDestination()
        }

        fun resolveStartDestination() {
            viewModelScope.launch {
                val result = remoteAuthRepository.restoreSession()
                _startDestination.value = startDestinationFor(result)
            }
        }
    }

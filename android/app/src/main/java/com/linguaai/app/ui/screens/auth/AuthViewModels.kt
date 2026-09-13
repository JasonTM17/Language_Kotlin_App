package com.linguaai.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.usecase.LoginUseCase
import com.linguaai.app.domain.usecase.RegisterUseCase
import com.linguaai.app.ui.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot navigation signals raised by the auth screens. */
sealed interface AuthEvent {
    data object Authenticated : AuthEvent
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val formError: String? = null,
    val isLoading: Boolean = false,
) {
    val isSubmitEnabled: Boolean get() = !isLoading && email.isNotBlank() && password.isNotBlank()
}

sealed interface LoginEvent {
    data class EmailChanged(
        val value: String,
    ) : LoginEvent

    data class PasswordChanged(
        val value: String,
    ) : LoginEvent

    data object Submit : LoginEvent
}

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val loginUseCase: LoginUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(LoginUiState())
        val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

        private val events = Channel<AuthEvent>(Channel.BUFFERED)
        val authEvents = events.receiveAsFlow()

        fun onEvent(event: LoginEvent) {
            when (event) {
                is LoginEvent.EmailChanged ->
                    _uiState.update {
                        it.copy(email = event.value, emailError = null, formError = null)
                    }
                is LoginEvent.PasswordChanged ->
                    _uiState.update {
                        it.copy(password = event.value, passwordError = null, formError = null)
                    }
                LoginEvent.Submit -> submit()
            }
        }

        private fun submit() {
            val current = _uiState.value
            if (current.isLoading) return
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, formError = null) }
                when (val result = loginUseCase(current.email, current.password)) {
                    is AppResult.Success -> {
                        _uiState.update { it.copy(isLoading = false) }
                        events.send(AuthEvent.Authenticated)
                    }
                    is AppResult.Failure ->
                        _uiState.update { state ->
                            state.copy(isLoading = false).applyError(result.error)
                        }
                }
            }
        }

        private fun LoginUiState.applyError(error: AppError): LoginUiState =
            when (error) {
                is AppError.Validation ->
                    when (error.field) {
                        "email" -> copy(emailError = error.reason)
                        "password" -> copy(passwordError = error.reason)
                        else -> copy(formError = error.reason)
                    }
                else -> copy(formError = error.toUserMessage())
            }
    }

data class RegisterUiState(
    val email: String = "",
    val username: String = "",
    val password: String = "",
    val emailError: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val formError: String? = null,
    val isLoading: Boolean = false,
) {
    val isSubmitEnabled: Boolean get() = !isLoading && email.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

sealed interface RegisterEvent {
    data class EmailChanged(
        val value: String,
    ) : RegisterEvent

    data class UsernameChanged(
        val value: String,
    ) : RegisterEvent

    data class PasswordChanged(
        val value: String,
    ) : RegisterEvent

    data object Submit : RegisterEvent
}

@HiltViewModel
class RegisterViewModel
    @Inject
    constructor(
        private val registerUseCase: RegisterUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RegisterUiState())
        val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

        private val events = Channel<AuthEvent>(Channel.BUFFERED)
        val authEvents = events.receiveAsFlow()

        fun onEvent(event: RegisterEvent) {
            when (event) {
                is RegisterEvent.EmailChanged ->
                    _uiState.update {
                        it.copy(email = event.value, emailError = null, formError = null)
                    }
                is RegisterEvent.UsernameChanged ->
                    _uiState.update {
                        it.copy(username = event.value, usernameError = null, formError = null)
                    }
                is RegisterEvent.PasswordChanged ->
                    _uiState.update {
                        it.copy(password = event.value, passwordError = null, formError = null)
                    }
                RegisterEvent.Submit -> submit()
            }
        }

        private fun submit() {
            val current = _uiState.value
            if (current.isLoading) return
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, formError = null) }
                when (val result = registerUseCase(current.email, current.username, current.password)) {
                    is AppResult.Success -> {
                        _uiState.update { it.copy(isLoading = false) }
                        events.send(AuthEvent.Authenticated)
                    }
                    is AppResult.Failure ->
                        _uiState.update { state ->
                            state.copy(isLoading = false).applyError(result.error)
                        }
                }
            }
        }

        private fun RegisterUiState.applyError(error: AppError): RegisterUiState =
            when (error) {
                is AppError.Validation ->
                    when (error.field) {
                        "email" -> copy(emailError = error.reason)
                        "username" -> copy(usernameError = error.reason)
                        "password" -> copy(passwordError = error.reason)
                        else -> copy(formError = error.reason)
                    }
                else -> copy(formError = error.toUserMessage())
            }
    }

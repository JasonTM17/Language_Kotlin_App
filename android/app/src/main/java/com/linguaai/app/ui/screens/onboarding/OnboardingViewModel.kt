package com.linguaai.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.remote.dto.LanguageDto
import com.linguaai.app.data.remote.dto.UpdateProfileRequestDto
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.LearningContentRepository
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

/** Choice catalogue shown during onboarding. */
object OnboardingCatalog {
    val goals =
        listOf(
            "Daily communication",
            "Travel",
            "University",
            "Work",
            "JLPT",
            "TOEIC",
            "IELTS",
            "Vocabulary",
            "Grammar",
            "Speaking",
        )
    val dailyGoals = listOf(5, 10, 20, 30, 60)
}

enum class OnboardingStep(
    val title: String,
) {
    LANGUAGE("What do you want to learn?"),
    LEVEL("How strong are you now?"),
    GOAL("What is your goal?"),
    DAILY("Daily goal"),
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.LANGUAGE,
    val languages: List<LanguageDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val selectedLanguageId: Long? = null,
    val selectedLevel: String? = null,
    val selectedGoal: String? = null,
    val selectedDailyGoal: Int? = null,
) {
    val canContinue: Boolean
        get() =
            when (step) {
                OnboardingStep.LANGUAGE -> selectedLanguageId != null
                OnboardingStep.LEVEL -> selectedLevel != null
                OnboardingStep.GOAL -> selectedGoal != null
                OnboardingStep.DAILY -> selectedDailyGoal != null
            }

    val currentLevels: List<String>
        get() = languages.firstOrNull { it.id == selectedLanguageId }?.levels ?: emptyList()
}

sealed interface OnboardingEvent {
    data object Completed : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val learningContentRepository: LearningContentRepository,
        private val remoteAuthRepository: RemoteAuthRepository,
        private val settingsDataStore: SettingsDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OnboardingUiState(isLoading = true))
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        private val channel = Channel<OnboardingEvent>(Channel.BUFFERED)
        val events = channel.receiveAsFlow()

        init {
            loadLanguages()
        }

        fun loadLanguages() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = learningContentRepository.languages()) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(isLoading = false, languages = result.data)
                        }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = result.error.toUserMessage())
                        }
                }
            }
        }

        fun selectLanguage(languageId: Long) =
            _uiState.update {
                it.copy(selectedLanguageId = languageId, selectedLevel = null)
            }

        fun selectLevel(level: String) = _uiState.update { it.copy(selectedLevel = level) }

        fun selectGoal(goal: String) = _uiState.update { it.copy(selectedGoal = goal) }

        fun selectDailyGoal(minutes: Int) = _uiState.update { it.copy(selectedDailyGoal = minutes) }

        fun next() {
            val state = _uiState.value
            if (!state.canContinue) return
            if (state.step == OnboardingStep.DAILY) {
                submit()
            } else {
                val nextStep = OnboardingStep.entries[_uiState.value.step.ordinal + 1]
                _uiState.update { it.copy(step = nextStep) }
            }
        }

        fun back() {
            if (_uiState.value.step == OnboardingStep.LANGUAGE) return
            val previous = OnboardingStep.entries[_uiState.value.step.ordinal - 1]
            _uiState.update { it.copy(step = previous) }
        }

        private fun submit() {
            val state = _uiState.value
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, error = null) }
                val request =
                    UpdateProfileRequestDto(
                        languageId = state.selectedLanguageId,
                        level = state.selectedLevel,
                        goal = state.selectedGoal,
                        dailyGoalMinutes = state.selectedDailyGoal,
                        onboarded = true,
                    )
                when (val result = remoteAuthRepository.updateProfile(request)) {
                    is AppResult.Success -> {
                        settingsDataStore.setOnboardingCompleted()
                        result.data.dailyGoalMinutes.takeIf { it > 0 }?.let {
                            settingsDataStore.setDailyGoalMinutes(it)
                        }
                        _uiState.update { it.copy(isSubmitting = false) }
                        channel.send(OnboardingEvent.Completed)
                    }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(isSubmitting = false, error = result.error.toUserMessage())
                        }
                }
            }
        }
    }

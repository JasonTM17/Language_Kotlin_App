package com.linguaai.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.repository.ProfileData
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.LearningContentRepository
import com.linguaai.app.ui.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val profile: ProfileData? = null,
    val dailyGoalMinutes: Int = 20,
    val todayMinutes: Int = 0,
    val streakDays: Int = 0,
    val dueVocabularyCount: Int = 0,
    val continueLesson: LessonSummaryDto? = null,
    val error: String? = null,
    val isOffline: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val remoteAuthRepository: RemoteAuthRepository,
    private val learningContentRepository: LearningContentRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val profileResult = remoteAuthRepository.fetchProfile()) {
                is AppResult.Success -> _uiState.update { state ->
                    state.copy(
                        profile = profileResult.data,
                        dailyGoalMinutes = profileResult.data.dailyGoalMinutes,
                    )
                }
                is AppResult.Failure -> _uiState.update { state ->
                    state.copy(
                        error = profileResult.error.toUserMessage(),
                        isOffline = profileResult.error == com.linguaai.app.domain.model.AppError.NetworkUnavailable,
                    )
                }
            }

            val goal = settingsDataStore.dailyGoalMinutes.first()
            _uiState.update { it.copy(dailyGoalMinutes = goal) }

            // Continue learning: the first lesson for the learner's language.
            val languageId = _uiState.value.profile?.languageId
            if (languageId != null) {
                when (val refresh = learningContentRepository.refreshLessons(languageId, null)) {
                    is AppResult.Failure -> if (_uiState.value.error == null) {
                        _uiState.update { it.copy(error = refresh.error.toUserMessage()) }
                    }
                    else -> Unit
                }
                val lessons = learningContentRepository.observeLessons(languageId, null).first()
                _uiState.update { it.copy(continueLesson = lessons.firstOrNull()) }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }
}

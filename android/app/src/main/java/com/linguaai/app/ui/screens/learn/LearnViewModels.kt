package com.linguaai.app.ui.screens.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.remote.dto.LessonDto
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.repository.ProfileData
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.LearningContentRepository
import com.linguaai.app.ui.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LearnUiState(
    val isLoading: Boolean = true,
    val lessons: List<LessonSummaryDto> = emptyList(),
    val selectedLevel: String? = null,
    val isOffline: Boolean = false,
    val error: String? = null,
    val languageId: Long? = null,
    val firstQuizId: Long? = null,
)

@HiltViewModel
class LearnViewModel @Inject constructor(
    private val learningContentRepository: LearningContentRepository,
    private val remoteAuthRepository: RemoteAuthRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LearnUiState())
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val languageId = currentLanguageId()
            launchLessons(languageId)
        }
    }

    fun refresh() {
        viewModelScope.launch { launchLessons(currentLanguageId()) }
    }

    fun selectLevel(level: String?) {
        _uiState.update { it.copy(selectedLevel = level) }
        viewModelScope.launch { launchLessons(currentLanguageId(), level) }
    }

    private suspend fun launchLessons(languageId: Long?, level: String? = _uiState.value.selectedLevel) {
        _uiState.update { it.copy(isLoading = true, error = null, languageId = languageId) }
        when (val refresh = learningContentRepository.refreshLessons(languageId, level)) {
            is AppResult.Failure -> _uiState.update {
                it.copy(
                    isOffline = refresh.error == com.linguaai.app.domain.model.AppError.NetworkUnavailable,
                    error = refresh.error.toUserMessage(),
                )
            }
            else -> _uiState.update { it.copy(isOffline = false, error = null) }
        }
        learningContentRepository.observeLessons(languageId, level).collect { lessons ->
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    lessons = lessons,
                    firstQuizId = state.firstQuizId ?: defaultQuizFor(languageId),
                )
            }
        }
    }

    private fun defaultQuizFor(languageId: Long?): Long? = when (languageId) {
        1L -> 1L // Japanese N5 Vocabulary Check
        2L -> 4L // English A1 Vocabulary Check
        else -> null
    }

    private suspend fun currentLanguageId(): Long? {
        val profile = remoteAuthRepository.fetchProfile()
        return when (profile) {
            is AppResult.Success -> profile.data.languageId
            is AppResult.Failure -> null
        }
    }
}

data class LessonDetailUiState(
    val isLoading: Boolean = true,
    val lesson: LessonDto? = null,
    val completed: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LessonDetailViewModel @Inject constructor(
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val learningContentRepository: LearningContentRepository,
    private val syncDao: com.linguaai.app.data.local.dao.SyncDao,
) : ViewModel() {

    private val lessonId: Long = checkNotNull(savedStateHandle["lessonId"])

    private val _uiState = MutableStateFlow(LessonDetailUiState())
    val uiState: StateFlow<LessonDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = learningContentRepository.refreshLesson(lessonId)) {
                is AppResult.Success -> _uiState.update { it.copy(isLoading = false, lesson = result.data) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error.toUserMessage())
                }
            }
        }
    }

    /** Records a meaningful learning event for streaks and progress sync. */
    fun markCompleted() {
        viewModelScope.launch {
            val minutes = _uiState.value.lesson?.estimatedMinutes ?: 5
            syncDao.enqueue(
                com.linguaai.app.data.local.entity.PendingSyncOpEntity(
                    operationId = java.util.UUID.randomUUID().toString(),
                    eventType = "LESSON_COMPLETED",
                    refId = lessonId,
                    minutes = minutes,
                    occurredAt = System.currentTimeMillis(),
                ),
            )
            _uiState.update { it.copy(completed = true) }
        }
    }
}

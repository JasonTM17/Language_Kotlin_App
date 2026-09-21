package com.linguaai.app.ui.screens.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.R
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.remote.dto.LessonDto
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.LearningContentRepository
import com.linguaai.app.ui.util.DEFAULT_LANGUAGE_LEVELS
import com.linguaai.app.ui.util.UiMessage
import com.linguaai.app.ui.util.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Language and quiz identifiers from the seeded catalogue. Naming them keeps
// `defaultQuizFor` readable and stops a bare literal drifting unnoticed.
private const val JAPANESE_LANGUAGE_ID = 1L
private const val ENGLISH_LANGUAGE_ID = 2L
private const val JAPANESE_N5_VOCABULARY_QUIZ_ID = 1L
private const val ENGLISH_A1_VOCABULARY_QUIZ_ID = 4L

/** Credited when a lesson does not state its own duration. */
private const val DEFAULT_LESSON_MINUTES = 5

data class LearnUiState(
    val isLoading: Boolean = true,
    val lessons: List<LessonSummaryDto> = emptyList(),
    val availableLevels: List<String> = DEFAULT_LANGUAGE_LEVELS,
    val selectedLevel: String? = null,
    val isOffline: Boolean = false,
    val error: UiMessage? = null,
    val languageId: Long? = null,
    val firstQuizId: Long? = null,
)

@HiltViewModel
class LearnViewModel
    @Inject
    constructor(
        private val learningContentRepository: LearningContentRepository,
        private val remoteAuthRepository: RemoteAuthRepository,
        private val settingsDataStore: SettingsDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(LearnUiState())
        val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()
        private var lessonsJob: Job? = null

        init {
            requestLessons(loadLevels = true)
        }

        fun refresh() {
            requestLessons()
        }

        fun selectLevel(level: String?) {
            _uiState.update { it.copy(selectedLevel = level) }
            requestLessons(level)
        }

        private fun requestLessons(
            level: String? = _uiState.value.selectedLevel,
            loadLevels: Boolean = false,
        ) {
            lessonsJob?.cancel()
            lessonsJob =
                viewModelScope.launch {
                    val languageId = currentLanguageId()
                    if (languageId == null) {
                        showMissingLanguage()
                        return@launch
                    }
                    if (loadLevels) loadAvailableLevels(languageId)
                    launchLessons(languageId, level)
                }
        }

        private suspend fun launchLessons(
            languageId: Long,
            level: String? = _uiState.value.selectedLevel,
        ) {
            _uiState.update { it.copy(isLoading = true, error = null, languageId = languageId) }
            when (val refresh = learningContentRepository.refreshLessons(languageId, level)) {
                is AppResult.Failure ->
                    _uiState.update {
                        it.copy(
                            isOffline = refresh.error == com.linguaai.app.domain.model.AppError.NetworkUnavailable,
                            error = refresh.error.toUiMessage(),
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

        private fun defaultQuizFor(languageId: Long?): Long? =
            when (languageId) {
                JAPANESE_LANGUAGE_ID -> JAPANESE_N5_VOCABULARY_QUIZ_ID
                ENGLISH_LANGUAGE_ID -> ENGLISH_A1_VOCABULARY_QUIZ_ID
                else -> null
            }

        private suspend fun currentLanguageId(): Long? {
            val profile = remoteAuthRepository.fetchProfile()
            return when (profile) {
                is AppResult.Success -> profile.data.languageId
                is AppResult.Failure -> settingsDataStore.learningLanguageId.first()
            }
        }

        private fun showMissingLanguage() {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    lessons = emptyList(),
                    languageId = null,
                    error = UiMessage(R.string.msg_need_language_lessons),
                )
            }
        }

        private suspend fun loadAvailableLevels(languageId: Long?) {
            when (val result = learningContentRepository.languages()) {
                is AppResult.Success ->
                    result.data
                        .firstOrNull { it.id == languageId }
                        ?.levels
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { levels -> _uiState.update { it.copy(availableLevels = levels) } }
                is AppResult.Failure -> Unit
            }
        }
    }

data class LessonDetailUiState(
    val isLoading: Boolean = true,
    val lesson: LessonDto? = null,
    val completed: Boolean = false,
    val error: UiMessage? = null,
)

@HiltViewModel
class LessonDetailViewModel
    @Inject
    constructor(
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
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = result.error.toUiMessage())
                        }
                }
            }
        }

        /** Records a meaningful learning event for streaks and progress sync. */
        fun markCompleted() {
            viewModelScope.launch {
                val minutes = _uiState.value.lesson?.estimatedMinutes ?: DEFAULT_LESSON_MINUTES
                syncDao.enqueue(
                    com.linguaai.app.data.local.entity.PendingSyncOpEntity(
                        operationId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        eventType = com.linguaai.app.data.remote.dto.ProgressEventTypes.LESSON_COMPLETED,
                        refId = lessonId,
                        minutes = minutes,
                        occurredAt = System.currentTimeMillis(),
                    ),
                )
                _uiState.update { it.copy(completed = true) }
            }
        }
    }

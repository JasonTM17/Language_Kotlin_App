package com.linguaai.app.ui.screens.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.local.dao.SyncDao
import com.linguaai.app.data.local.entity.PendingSyncOpEntity
import com.linguaai.app.data.remote.dto.QuizDto
import com.linguaai.app.data.remote.dto.QuizResultDto
import com.linguaai.app.data.remote.dto.QuizSubmissionAnswerDto
import com.linguaai.app.data.remote.dto.QuizSubmissionDto
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.LearningContentRepository
import com.linguaai.app.ui.util.UiMessage
import com.linguaai.app.ui.util.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuizUiState(
    val isLoading: Boolean = true,
    val quiz: QuizDto? = null,
    val answers: Map<Long, String> = emptyMap(),
    val isSubmitting: Boolean = false,
    val result: QuizResultDto? = null,
    val error: UiMessage? = null,
) {
    val allAnswered: Boolean
        get() = quiz != null && answers.size == quiz!!.questions.size
}

sealed interface QuizEvent {
    data class AnswerSelected(
        val questionId: Long,
        val option: String,
    ) : QuizEvent

    data object Submit : QuizEvent

    data object Retry : QuizEvent
}

@HiltViewModel
class QuizViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val learningContentRepository: LearningContentRepository,
        private val settingsDataStore: SettingsDataStore,
        private val syncDao: SyncDao,
    ) : ViewModel() {
        private val quizId: Long = checkNotNull(savedStateHandle["quizId"])
        val scoreHistory = settingsDataStore.quizScoreHistory
        private val startedAt = System.currentTimeMillis()

        private val _uiState = MutableStateFlow(QuizUiState())
        val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = learningContentRepository.quiz(quizId)) {
                    is AppResult.Success -> _uiState.update { it.copy(isLoading = false, quiz = result.data) }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = result.error.toUiMessage())
                        }
                }
            }
        }

        fun onEvent(event: QuizEvent) {
            when (event) {
                is QuizEvent.AnswerSelected ->
                    _uiState.update {
                        it.copy(answers = it.answers + (event.questionId to event.option))
                    }
                QuizEvent.Submit -> submit()
                QuizEvent.Retry -> load()
            }
        }

        private fun submit() {
            val state = _uiState.value
            if (state.isSubmitting || state.quiz == null) return
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, error = null) }
                val submission =
                    QuizSubmissionDto(
                        answers =
                            state.answers.map { (questionId, answer) ->
                                QuizSubmissionAnswerDto(questionId = questionId, answer = answer)
                            },
                        durationSeconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt(),
                    )
                when (val result = learningContentRepository.submitQuiz(quizId, submission)) {
                    is AppResult.Success -> {
                        val total = result.data.total
                        if (total > 0) {
                            settingsDataStore.recordQuizScore(result.data.score * 100 / total)
                        }
                        settingsDataStore.recordQuestProgress(com.linguaai.app.domain.model.DailyQuestType.QUIZ)
                        syncDao.enqueue(
                            PendingSyncOpEntity(
                                operationId =
                                    java.util.UUID
                                        .randomUUID()
                                        .toString(),
                                eventType = com.linguaai.app.data.remote.dto.ProgressEventTypes.QUIZ_ATTEMPT,
                                refId = quizId,
                                minutes = 5,
                                occurredAt = System.currentTimeMillis(),
                            ),
                        )
                        _uiState.update { it.copy(isSubmitting = false, result = result.data) }
                    }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(isSubmitting = false, error = result.error.toUiMessage())
                        }
                }
            }
        }
    }

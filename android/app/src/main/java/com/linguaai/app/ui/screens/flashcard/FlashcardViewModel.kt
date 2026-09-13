package com.linguaai.app.ui.screens.flashcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.local.dao.SyncDao
import com.linguaai.app.data.local.dao.VocabularyDao
import com.linguaai.app.data.local.entity.PendingSyncOpEntity
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.domain.repository.LearningContentRepository
import com.linguaai.app.domain.srs.ReviewGrade
import com.linguaai.app.domain.srs.ReviewScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The scheduler works in minutes; the database stores absolute epoch millis. */
private const val MILLIS_PER_MINUTE = 60_000L

data class FlashcardUiState(
    val isLoading: Boolean = true,
    val queue: List<VocabularyCard> = emptyList(),
    val currentIndex: Int = 0,
    val isRevealed: Boolean = false,
    val reviewedCount: Int = 0,
    val finished: Boolean = false,
    val error: String? = null,
) {
    val current: VocabularyCard? get() = queue.getOrNull(currentIndex)
}

sealed interface FlashcardEvent {
    data class Grade(
        val grade: ReviewGrade,
    ) : FlashcardEvent

    data object Reveal : FlashcardEvent
}

@HiltViewModel
class FlashcardViewModel
    @Inject
    constructor(
        private val vocabularyDao: VocabularyDao,
        private val syncDao: SyncDao,
        private val learningContentRepository: LearningContentRepository,
        private val reviewScheduler: ReviewScheduler,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(FlashcardUiState())
        val uiState: StateFlow<FlashcardUiState> = _uiState.asStateFlow()

        init {
            loadQueue()
        }

        fun loadQueue() {
            viewModelScope.launch {
                // Refresh cache first (network is optional); due words come from Room.
                learningContentRepository.refreshVocabulary(null, null, null, null)
                val due =
                    vocabularyDao
                        .dueForReview(now = System.currentTimeMillis(), limit = 20)
                        .map { entity ->
                            VocabularyCard(
                                id = entity.id,
                                languageId = entity.languageId,
                                level = entity.level,
                                word = entity.word,
                                reading = entity.reading,
                                pronunciation = entity.pronunciation,
                                meaning = entity.meaning,
                                example = entity.example,
                                exampleTranslation = entity.exampleTranslation,
                                category = entity.category,
                                favorite = entity.favorite,
                                masteryLevel = entity.masteryLevel,
                            )
                        }
                _uiState.update { it.copy(isLoading = false, queue = due, finished = due.isEmpty()) }
            }
        }

        fun onEvent(event: FlashcardEvent) {
            when (event) {
                FlashcardEvent.Reveal -> _uiState.update { it.copy(isRevealed = true) }
                is FlashcardEvent.Grade -> submitGrade(event.grade)
            }
        }

        private fun submitGrade(grade: ReviewGrade) {
            val card = _uiState.value.current ?: return
            viewModelScope.launch {
                val entity = vocabularyDao.findById(card.id) ?: return@launch
                val now = System.currentTimeMillis()
                val nextMastery = reviewScheduler.nextMastery(entity.masteryLevel, grade)
                val nextReview = now + reviewScheduler.nextIntervalMinutes(entity.masteryLevel, grade) * MILLIS_PER_MINUTE

                vocabularyDao.upsert(
                    entity.copy(
                        masteryLevel = nextMastery,
                        reviewCount = entity.reviewCount + 1,
                        correctCount = entity.correctCount + if (grade == ReviewGrade.AGAIN) 0 else 1,
                        wrongCount = entity.wrongCount + if (grade == ReviewGrade.AGAIN) 1 else 0,
                        lastReviewedAt = now,
                        nextReviewAt = nextReview,
                    ),
                )
                // Meaningful learning event -> outbox for idempotent background sync.
                syncDao.enqueue(
                    PendingSyncOpEntity(
                        operationId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        eventType = com.linguaai.app.data.remote.dto.ProgressEventTypes.FLASHCARD_REVIEW,
                        refId = card.id,
                        minutes = 1,
                        occurredAt = now,
                    ),
                )
                _uiState.update { state ->
                    val nextIndex = state.currentIndex + 1
                    state.copy(
                        isRevealed = false,
                        currentIndex = nextIndex,
                        reviewedCount = state.reviewedCount + 1,
                        finished = nextIndex >= state.queue.size,
                    )
                }
            }
        }
    }

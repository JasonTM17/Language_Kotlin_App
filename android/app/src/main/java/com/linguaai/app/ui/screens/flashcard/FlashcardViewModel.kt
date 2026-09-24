package com.linguaai.app.ui.screens.flashcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.R
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.local.dao.VocabularyDao
import com.linguaai.app.data.local.entity.VocabularyEntity
import com.linguaai.app.data.remote.dto.ProgressEventTypes
import com.linguaai.app.data.remote.dto.VocabularyProgressSnapshotDto
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.domain.repository.LearningContentRepository
import com.linguaai.app.domain.srs.ReviewGrade
import com.linguaai.app.domain.srs.ReviewScheduler
import com.linguaai.app.ui.util.UiMessage
import com.linguaai.app.work.ProgressEventRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val isSubmittingGrade: Boolean = false,
    val finished: Boolean = false,
    val error: UiMessage? = null,
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
        private val learningContentRepository: LearningContentRepository,
        private val remoteAuthRepository: RemoteAuthRepository,
        private val settingsDataStore: SettingsDataStore,
        private val reviewScheduler: ReviewScheduler,
        private val progressEventRecorder: ProgressEventRecorder,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(FlashcardUiState())
        val uiState: StateFlow<FlashcardUiState> = _uiState.asStateFlow()

        init {
            loadQueue()
        }

        fun loadQueue() {
            viewModelScope.launch {
                val languageId =
                    when (val profile = remoteAuthRepository.fetchProfile()) {
                        is AppResult.Success -> profile.data.languageId
                        is AppResult.Failure -> settingsDataStore.learningLanguageId.first()
                    }
                if (languageId == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            queue = emptyList(),
                            finished = true,
                            error = UiMessage(R.string.msg_need_language_review),
                        )
                    }
                    return@launch
                }

                // Refresh cache first (network is optional); due words stay scoped
                // to the learner's language so an old Japanese cache can never
                // leak into a Chinese, Korean or other multilingual session.
                learningContentRepository.refreshVocabulary(languageId, null, null, null)
                val due =
                    vocabularyDao
                        .dueForReview(languageId = languageId, now = System.currentTimeMillis(), limit = 20)
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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        queue = due,
                        finished = due.isEmpty(),
                        error = null,
                    )
                }
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
            if (_uiState.value.isSubmittingGrade) return
            _uiState.update { it.copy(isSubmittingGrade = true) }
            viewModelScope.launch {
                try {
                    val entity = vocabularyDao.findById(card.id) ?: return@launch
                    val now = System.currentTimeMillis()
                    val nextMastery = reviewScheduler.nextMastery(entity.masteryLevel, grade)
                    val nextReview = now + reviewScheduler.nextIntervalMinutes(entity.masteryLevel, grade) * MILLIS_PER_MINUTE

                    val updated =
                        entity.copy(
                            masteryLevel = nextMastery,
                            reviewCount = entity.reviewCount + 1,
                            correctCount = entity.correctCount + if (grade == ReviewGrade.AGAIN) 0 else 1,
                            wrongCount = entity.wrongCount + if (grade == ReviewGrade.AGAIN) 1 else 0,
                            lastReviewedAt = now,
                            nextReviewAt = nextReview,
                            stateUpdatedAt = maxOf(entity.stateUpdatedAt ?: 0L, now) + 1L,
                        )
                    progressEventRecorder.record(
                        eventType = ProgressEventTypes.FLASHCARD_REVIEW,
                        refId = card.id,
                        minutes = 1,
                        vocabularyProgress = updated.toProgressSnapshot(),
                        localUpdate = { vocabularyDao.upsert(updated) },
                    )
                    settingsDataStore.recordQuestProgress(com.linguaai.app.domain.model.DailyQuestType.FLASHCARDS)
                    _uiState.update { state ->
                        val nextIndex = state.currentIndex + 1
                        state.copy(
                            isRevealed = false,
                            currentIndex = nextIndex,
                            reviewedCount = state.reviewedCount + 1,
                            finished = nextIndex >= state.queue.size,
                        )
                    }
                } finally {
                    // A failed local write must not leave the review controls
                    // permanently disabled for the rest of the session.
                    _uiState.update { it.copy(isSubmittingGrade = false) }
                }
            }
        }

        fun startCramSession(favoritesOnly: Boolean = false) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val languageId =
                    when (val profile = remoteAuthRepository.fetchProfile()) {
                        is AppResult.Success -> profile.data.languageId
                        is AppResult.Failure -> settingsDataStore.learningLanguageId.first()
                    }
                if (languageId == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                val entities =
                    if (favoritesOnly) {
                        vocabularyDao.observeFavorites(languageId).first()
                    } else {
                        vocabularyDao.observeVocabulary(languageId, null, null, null).first().take(CRAM_BATCH_LIMIT)
                    }
                val cards =
                    entities.map { entity ->
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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        queue = cards,
                        currentIndex = 0,
                        isRevealed = false,
                        finished = cards.isEmpty(),
                        error = null,
                    )
                }
            }
        }
    }

private const val CRAM_BATCH_LIMIT = 20

private fun VocabularyEntity.toProgressSnapshot(): VocabularyProgressSnapshotDto =
    VocabularyProgressSnapshotDto(
        favorite = favorite,
        masteryLevel = masteryLevel,
        reviewCount = reviewCount,
        correctCount = correctCount,
        wrongCount = wrongCount,
        lastReviewedAtEpochMillis = lastReviewedAt,
        nextReviewAtEpochMillis = nextReviewAt,
        stateUpdatedAtEpochMillis = stateUpdatedAt,
    )

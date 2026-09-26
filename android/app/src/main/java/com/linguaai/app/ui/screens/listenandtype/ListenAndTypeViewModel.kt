package com.linguaai.app.ui.screens.listenandtype

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.datastore.cachedLearningLanguageCode
import com.linguaai.app.data.local.dao.VocabularyDao
import com.linguaai.app.data.local.entity.VocabularyEntity
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.model.ListenAndTypeRound
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.domain.repository.LearningContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListenAndTypeUiState(
    val isLoading: Boolean = true,
    val round: ListenAndTypeRound? = null,
    val languageCode: String? = null,
    val loadError: ListenAndTypeLoadError? = null,
)

enum class ListenAndTypeLoadError {
    LANGUAGE_REQUIRED,
    LANGUAGE_CODE_UNAVAILABLE,
    LOAD_FAILED,
}

@HiltViewModel
class ListenAndTypeViewModel
    @Inject
    constructor(
        private val vocabularyDao: VocabularyDao,
        private val learningContentRepository: LearningContentRepository,
        private val remoteAuthRepository: RemoteAuthRepository,
        private val settingsDataStore: SettingsDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ListenAndTypeUiState())
        val uiState: StateFlow<ListenAndTypeUiState> = _uiState.asStateFlow()

        init {
            loadRound()
        }

        fun loadRound() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, loadError = null) }
                try {
                    val languageId = currentLanguageId()
                    if (languageId == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                round = ListenAndTypeRound.start(languageId = 0, candidates = emptyList()),
                                languageCode = null,
                                loadError = ListenAndTypeLoadError.LANGUAGE_REQUIRED,
                            )
                        }
                        return@launch
                    }

                    val languageCode = languageCode(languageId)
                    val candidates =
                        vocabularyDao
                            .dueForReview(
                                languageId = languageId,
                                now = System.currentTimeMillis(),
                                limit = ListenAndTypeRound.MAX_WORDS,
                            ).map(VocabularyEntity::toVocabularyCard)
                    val round = ListenAndTypeRound.start(languageId, candidates)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            round = round,
                            languageCode = languageCode,
                            loadError =
                                if (languageCode == null && !round.isEmpty) {
                                    ListenAndTypeLoadError.LANGUAGE_CODE_UNAVAILABLE
                                } else {
                                    null
                                },
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.update {
                        it.copy(isLoading = false, loadError = ListenAndTypeLoadError.LOAD_FAILED)
                    }
                }
            }
        }

        fun submitAnswer(answer: String) {
            _uiState.update { state ->
                val round = state.round ?: return@update state
                state.copy(round = round.submitAnswer(answer))
            }
        }

        fun advance() {
            _uiState.update { state ->
                val round = state.round ?: return@update state
                state.copy(round = round.advance())
            }
        }

        fun retryRound() {
            _uiState.update { state ->
                val round = state.round ?: return@update state
                state.copy(round = round.retry())
            }
        }

        private suspend fun currentLanguageId(): Long? =
            when (val profile = remoteAuthRepository.fetchProfile()) {
                is AppResult.Success -> profile.data.languageId
                is AppResult.Failure -> settingsDataStore.learningLanguageId.first()
            }

        private suspend fun languageCode(languageId: Long): String? =
            when (val languages = learningContentRepository.languages()) {
                is AppResult.Success ->
                    languages.data.firstOrNull { it.id == languageId }?.code.also { code ->
                        code?.let { settingsDataStore.setLearningLanguageCode(languageId, it) }
                    }
                is AppResult.Failure -> {
                    val (cachedLanguageId, cachedLanguageCode) = settingsDataStore.cachedLearningLanguage()
                    cachedLearningLanguageCode(languageId, cachedLanguageId, cachedLanguageCode)
                }
            }
    }

private fun VocabularyEntity.toVocabularyCard(): VocabularyCard =
    VocabularyCard(
        id = id,
        languageId = languageId,
        level = level,
        word = word,
        reading = reading,
        pronunciation = pronunciation,
        meaning = meaning,
        example = example,
        exampleTranslation = exampleTranslation,
        category = category,
        favorite = favorite,
        masteryLevel = masteryLevel,
    )

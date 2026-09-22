package com.linguaai.app.ui.screens.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.R
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.domain.repository.LearningContentRepository
import com.linguaai.app.ui.util.DEFAULT_LANGUAGE_LEVELS
import com.linguaai.app.ui.util.UiMessage
import com.linguaai.app.ui.util.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Waits for a typing pause before hitting the API on every keystroke. */
private const val SEARCH_DEBOUNCE_MILLIS = 300L

data class VocabularyUiState(
    val isLoading: Boolean = true,
    val vocabulary: List<VocabularyCard> = emptyList(),
    val availableLevels: List<String> = DEFAULT_LANGUAGE_LEVELS,
    val query: String = "",
    val selectedLevel: String? = null,
    val isOffline: Boolean = false,
    val error: UiMessage? = null,
)

sealed interface VocabularyEvent {
    data class SearchChanged(
        val value: String,
    ) : VocabularyEvent

    data class LevelSelected(
        val level: String?,
    ) : VocabularyEvent

    data class ToggleFavorite(
        val id: Long,
    ) : VocabularyEvent

    data object Retry : VocabularyEvent

    data object SearchCommitted : VocabularyEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class VocabularyViewModel
    @Inject
    constructor(
        private val learningContentRepository: LearningContentRepository,
        private val remoteAuthRepository: RemoteAuthRepository,
        private val settingsDataStore: SettingsDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(VocabularyUiState())
        val uiState: StateFlow<VocabularyUiState> = _uiState.asStateFlow()

        /** Recent catalogue searches, newest first, for the chip row. */
        val recentQueries: Flow<List<String>> = settingsDataStore.recentVocabQueries

        private val languageIdState = MutableStateFlow<Long?>(null)
        private val queryState = MutableStateFlow("")
        private val levelState = MutableStateFlow<String?>(null)
        private val refreshTick = MutableStateFlow(0)

        init {
            viewModelScope.launch {
                val languageId = currentLanguageId()
                if (languageId == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            vocabulary = emptyList(),
                            error = UiMessage(R.string.msg_need_language_vocab),
                        )
                    }
                    return@launch
                }
                languageIdState.value = languageId
                loadAvailableLevels(languageId)
                refresh()
                combine(queryState.debounce(SEARCH_DEBOUNCE_MILLIS).distinctUntilChanged(), levelState, refreshTick) { q, l, _ ->
                    Triple(q, l, languageIdState.value)
                }.distinctUntilChanged()
                    .flatMapLatest { (query, level, languageId) ->
                        learningContentRepository.observeVocabulary(
                            languageId = languageId,
                            level = level,
                            category = null,
                            query = query.ifBlank { null },
                        )
                    }.collect { words ->
                        _uiState.update { it.copy(isLoading = false, vocabulary = words) }
                    }
            }
        }

        fun onEvent(event: VocabularyEvent) {
            when (event) {
                is VocabularyEvent.SearchChanged -> {
                    _uiState.update { it.copy(query = event.value) }
                    queryState.value = event.value
                }
                is VocabularyEvent.LevelSelected -> {
                    _uiState.update { it.copy(selectedLevel = event.level) }
                    levelState.value = event.level
                    viewModelScope.launch { refresh() }
                }
                is VocabularyEvent.ToggleFavorite ->
                    viewModelScope.launch {
                        learningContentRepository.toggleFavorite(event.id)
                        refreshTick.value += 1
                    }
                VocabularyEvent.Retry -> viewModelScope.launch { refresh() }
                VocabularyEvent.SearchCommitted ->
                    viewModelScope.launch { settingsDataStore.rememberVocabQuery(_uiState.value.query) }
            }
        }

        fun refresh() {
            viewModelScope.launch {
                if (languageIdState.value == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            vocabulary = emptyList(),
                            error = UiMessage(R.string.msg_need_language_vocab),
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (
                    val result =
                        learningContentRepository.refreshVocabulary(
                            languageIdState.value,
                            levelState.value,
                            null,
                            null,
                        )
                ) {
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(isOffline = result.error == AppError.NetworkUnavailable, error = result.error.toUiMessage())
                        }
                    else -> _uiState.update { it.copy(isOffline = false, error = null) }
                }
            }
        }

        private suspend fun currentLanguageId(): Long? =
            when (
                val profile = remoteAuthRepository.fetchProfile()
            ) {
                is AppResult.Success -> profile.data.languageId
                is AppResult.Failure -> settingsDataStore.learningLanguageId.first()
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

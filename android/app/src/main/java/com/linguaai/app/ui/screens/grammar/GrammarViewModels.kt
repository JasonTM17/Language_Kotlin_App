package com.linguaai.app.ui.screens.grammar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.R
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.remote.dto.GrammarDto
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.LearningContentRepository
import com.linguaai.app.ui.util.UiMessage
import com.linguaai.app.ui.util.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GrammarListUiState(
    val isLoading: Boolean = true,
    val items: List<GrammarDto> = emptyList(),
    val isOffline: Boolean = false,
    val error: UiMessage? = null,
)

@HiltViewModel
class GrammarViewModel
    @Inject
    constructor(
        private val learningContentRepository: LearningContentRepository,
        private val remoteAuthRepository: RemoteAuthRepository,
        private val settingsDataStore: SettingsDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(GrammarListUiState())
        val uiState: StateFlow<GrammarListUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val languageId =
                    when (val profile = remoteAuthRepository.fetchProfile()) {
                        is AppResult.Success -> profile.data.languageId
                        is AppResult.Failure -> settingsDataStore.learningLanguageId.first()
                    }
                if (languageId == null) {
                    showMissingLanguage()
                    return@launch
                }
                refresh(languageId)
                learningContentRepository.observeGrammar(languageId, null).collect { items ->
                    _uiState.update { it.copy(isLoading = false, items = items) }
                }
            }
        }

        fun refresh() {
            viewModelScope.launch {
                val languageId =
                    when (val profile = remoteAuthRepository.fetchProfile()) {
                        is AppResult.Success -> profile.data.languageId
                        is AppResult.Failure -> settingsDataStore.learningLanguageId.first()
                    }
                if (languageId == null) {
                    showMissingLanguage()
                } else {
                    refresh(languageId)
                }
            }
        }

        private suspend fun refresh(languageId: Long) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = learningContentRepository.refreshGrammar(languageId, null)) {
                is AppResult.Failure ->
                    _uiState.update {
                        it.copy(isOffline = result.error == AppError.NetworkUnavailable, error = result.error.toUiMessage())
                    }
                else -> _uiState.update { it.copy(isOffline = false, error = null) }
            }
        }

        private fun showMissingLanguage() {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    items = emptyList(),
                    error = UiMessage(R.string.msg_need_language_grammar),
                )
            }
        }
    }

data class GrammarDetailUiState(
    val isLoading: Boolean = true,
    val grammar: GrammarDto? = null,
    val error: UiMessage? = null,
)

@HiltViewModel
class GrammarDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val learningContentRepository: LearningContentRepository,
    ) : ViewModel() {
        private val grammarId: Long = checkNotNull(savedStateHandle["grammarId"])

        private val _uiState = MutableStateFlow(GrammarDetailUiState())
        val uiState: StateFlow<GrammarDetailUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = learningContentRepository.grammarById(grammarId)) {
                    is AppResult.Success -> _uiState.update { it.copy(isLoading = false, grammar = result.data) }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = result.error.toUiMessage())
                        }
                }
            }
        }
    }

package com.linguaai.app.ui.screens.grammar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.remote.dto.GrammarDto
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.LearningContentRepository
import com.linguaai.app.ui.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GrammarListUiState(
    val isLoading: Boolean = true,
    val items: List<GrammarDto> = emptyList(),
    val isOffline: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GrammarViewModel
    @Inject
    constructor(
        private val learningContentRepository: LearningContentRepository,
        private val remoteAuthRepository: RemoteAuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(GrammarListUiState())
        val uiState: StateFlow<GrammarListUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val languageId =
                    when (val profile = remoteAuthRepository.fetchProfile()) {
                        is AppResult.Success -> profile.data.languageId
                        is AppResult.Failure -> null
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
                        is AppResult.Failure -> null
                    }
                refresh(languageId)
            }
        }

        private suspend fun refresh(languageId: Long?) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = learningContentRepository.refreshGrammar(languageId, null)) {
                is AppResult.Failure ->
                    _uiState.update {
                        it.copy(isOffline = result.error == AppError.NetworkUnavailable, error = result.error.toUserMessage())
                    }
                else -> _uiState.update { it.copy(isOffline = false, error = null) }
            }
        }
    }

data class GrammarDetailUiState(
    val isLoading: Boolean = true,
    val grammar: GrammarDto? = null,
    val error: String? = null,
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
                            it.copy(isLoading = false, error = result.error.toUserMessage())
                        }
                }
            }
        }
    }

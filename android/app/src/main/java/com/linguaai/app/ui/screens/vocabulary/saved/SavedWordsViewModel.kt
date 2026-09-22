package com.linguaai.app.ui.screens.vocabulary.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.domain.repository.LearningContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedWordsUiState(
    val isLoading: Boolean = true,
    val words: List<VocabularyCard> = emptyList(),
)

@HiltViewModel
class SavedWordsViewModel
    @Inject
    constructor(
        private val learningContentRepository: LearningContentRepository,
        private val remoteAuthRepository: RemoteAuthRepository,
        private val settingsDataStore: SettingsDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SavedWordsUiState())
        val uiState: StateFlow<SavedWordsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val languageId = currentLanguageId()
                if (languageId == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                learningContentRepository.observeFavorites(languageId).collect { words ->
                    _uiState.update { it.copy(isLoading = false, words = words) }
                }
            }
        }

        private suspend fun currentLanguageId(): Long? =
            when (val profile = remoteAuthRepository.fetchProfile()) {
                is AppResult.Success -> profile.data.languageId
                is AppResult.Failure -> settingsDataStore.learningLanguageId.first()
            }
    }

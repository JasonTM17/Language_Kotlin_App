package com.linguaai.app.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.remote.api.AiApi
import com.linguaai.app.data.remote.dto.GeneratedQuizQuestionDto
import com.linguaai.app.data.remote.dto.GenerateQuizRequestDto
import com.linguaai.app.data.remote.safeApiCall
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.ui.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiHomeUiState(
    val isLoading: Boolean = true,
    val conversations: List<com.linguaai.app.data.remote.dto.AiConversationDto> = emptyList(),
    val generatedQuiz: List<GeneratedQuizQuestionDto> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AiHomeViewModel @Inject constructor(
    private val aiApi: AiApi,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiHomeUiState())
    val uiState: StateFlow<AiHomeUiState> = _uiState.asStateFlow()

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = safeApiCall { aiApi.conversations() }) {
                is AppResult.Success -> _uiState.update { it.copy(isLoading = false, conversations = result.data) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error.toUserMessage())
                }
            }
        }
    }

    fun generateQuiz() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            when (val result = safeApiCall { aiApi.generateQuiz(GenerateQuizRequestDto(count = 5)) }) {
                is AppResult.Success -> _uiState.update { it.copy(isGenerating = false, generatedQuiz = result.data.questions) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isGenerating = false, error = result.error.toUserMessage())
                }
            }
        }
    }
}

package com.linguaai.app.ui.screens.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.local.dao.AiMessageCacheDao
import com.linguaai.app.data.local.entity.AiMessageCacheEntity
import com.linguaai.app.data.remote.api.AiApi
import com.linguaai.app.data.remote.dto.AiChatRequestDto
import com.linguaai.app.data.remote.safeApiCall
import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.util.NetworkMonitor
import com.linguaai.app.ui.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: String, // USER | ASSISTANT
    val content: String,
    val isPending: Boolean = false,
)

data class AiChatUiState(
    val isLoading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val conversationId: Long? = null,
    val isOffline: Boolean = false,
    val error: String? = null,
)

/**
 * Chat experience backed by the server-side AI tutor. Message history is
 * cached in Room so past conversations stay readable offline; sending always
 * requires connectivity (surfaced through a friendly banner, never a crash).
 */
@HiltViewModel
class AiChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aiApi: AiApi,
    private val cacheDao: AiMessageCacheDao,
    private val networkMonitor: NetworkMonitor,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val routeConversationId: Long? = savedStateHandle["conversationId"]
    private val routeMode: String = savedStateHandle["mode"] ?: "general"

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(isOffline = !networkMonitor.isOnline.value) }
        loadHistory()
    }

    fun onInputChanged(value: String) = _uiState.update { it.copy(input = value) }

    fun send() {
        val state = _uiState.value
        val text = state.input.trim()
        if (text.isEmpty() || state.isSending) return
        if (!networkMonitor.isOnline.value) {
            _uiState.update {
                it.copy(
                    isOffline = true,
                    error = "AI Tutor requires an internet connection. Your learning data is still available offline.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSending = true,
                    error = null,
                    input = "",
                    messages = it.messages + ChatMessage("USER", text) + ChatMessage("ASSISTANT", "", isPending = true),
                )
            }
            val request = buildRequest(text)
            when (val result = safeApiCall { aiApi.chat(request) }) {
                is AppResult.Success -> {
                    val conversationId = result.data.conversationId
                    cacheDao.insert(AiMessageCacheEntity(conversationId = conversationId, role = "USER", content = text))
                    cacheDao.insert(
                        AiMessageCacheEntity(conversationId = conversationId, role = "ASSISTANT", content = result.data.reply),
                    )
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            conversationId = conversationId,
                            messages = it.messages.dropLast(1) + ChatMessage("ASSISTANT", result.data.reply),
                        )
                    }
                }
                is AppResult.Failure -> _uiState.update { state ->
                    state.copy(
                        isSending = false,
                        error = result.error.toUserMessage(),
                        messages = state.messages.dropLast(2) + ChatMessage("USER", text),
                    )
                }
            }
        }
    }

    fun retryLast() {
        val lastUser = _uiState.value.messages.lastOrNull { it.role == "USER" } ?: return
        _uiState.update { it.copy(input = lastUser.content) }
        send()
    }

    private fun buildRequest(text: String): AiChatRequestDto = when (routeMode) {
        "grammar-explain" -> AiChatRequestDto(mode = "grammar-explain", message = text, contextGrammarId = routeConversationId)
        "lesson-context" -> AiChatRequestDto(mode = "general", message = text, contextLessonId = routeConversationId)
        "mistakes" -> AiChatRequestDto(mode = "mistakes-review", message = text)
        else -> AiChatRequestDto(
            conversationId = _uiState.value.conversationId ?: routeConversationId,
            mode = routeMode,
            message = text,
        )
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val conversationId = routeConversationId
            if (conversationId != null && routeMode == "conversation") {
                if (networkMonitor.isOnline.value) {
                    when (val result = safeApiCall { aiApi.messages(conversationId) }) {
                        is AppResult.Success -> {
                            cacheDao.clearConversation(conversationId)
                            cacheDao.insertAll(
                                result.data.map {
                                    AiMessageCacheEntity(
                                        conversationId = conversationId,
                                        role = it.role,
                                        content = it.content,
                                    )
                                },
                            )
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    conversationId = conversationId,
                                    messages = result.data.map { m -> ChatMessage(m.role, m.content) },
                                )
                            }
                            return@launch
                        }
                        is AppResult.Failure -> if (result.error != AppError.NetworkUnavailable) {
                            _uiState.update { it.copy(isLoading = false, error = result.error.toUserMessage()) }
                            return@launch
                        }
                    }
                }
                val cached = cacheDao.byConversation(conversationId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        conversationId = conversationId,
                        isOffline = !networkMonitor.isOnline.value,
                        messages = cached.map { m -> ChatMessage(m.role, m.content) },
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}

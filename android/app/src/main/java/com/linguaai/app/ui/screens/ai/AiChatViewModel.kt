package com.linguaai.app.ui.screens.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.local.dao.AiMessageCacheDao
import com.linguaai.app.data.local.entity.AiMessageCacheEntity
import com.linguaai.app.data.remote.api.AiApi
import com.linguaai.app.data.remote.dto.AiChatRequestDto
import com.linguaai.app.data.remote.dto.AiChatResponseDto
import com.linguaai.app.data.remote.dto.AiSourceDto
import com.linguaai.app.data.remote.dto.CorrectRequestDto
import com.linguaai.app.data.remote.dto.PracticeReplyRequestDto
import com.linguaai.app.data.remote.dto.PracticeScoreDto
import com.linguaai.app.data.remote.dto.PracticeStartRequestDto
import com.linguaai.app.data.remote.safeApiCall
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.ui.util.toUserMessage
import com.linguaai.app.util.ConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ChatMessage(
    val role: String, // USER | ASSISTANT
    val content: String,
    val isPending: Boolean = false,
    val sources: List<AiSourceDto> = emptyList(),
)

data class AiChatUiState(
    val isLoading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val conversationId: Long? = null,
    val mode: String = "general",
    val isOffline: Boolean = false,
    val isScoring: Boolean = false,
    val practiceScore: PracticeScoreDto? = null,
    val failedInput: String? = null,
    val error: String? = null,
)

/**
 * Chat experience backed by the server-side AI tutor. Message history is
 * cached in Room so past conversations stay readable offline; sending always
 * requires connectivity (surfaced through a friendly banner, never a crash).
 */
@HiltViewModel
class AiChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val aiApi: AiApi,
        private val cacheDao: AiMessageCacheDao,
        private val networkMonitor: ConnectivityMonitor,
    ) : ViewModel() {
        private val routeConversationId: Long? = savedStateHandle["conversationId"]
        private val routeMode: String = savedStateHandle["mode"] ?: "general"

        private val _uiState = MutableStateFlow(AiChatUiState(mode = routeMode))
        val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

        init {
            observeConnectivity()
            loadHistory()
        }

        fun onInputChanged(value: String) = _uiState.update { it.copy(input = value) }

        fun send() {
            val state = _uiState.value
            val text = state.input.trim()
            if (text.isEmpty() || state.isLoading || state.isSending) return
            if (!networkMonitor.isOnline.value) {
                _uiState.update {
                    it.copy(
                        isOffline = true,
                        error = OFFLINE_MESSAGE,
                    )
                }
                return
            }

            _uiState.update {
                it.copy(
                    isSending = true,
                    error = null,
                    failedInput = null,
                    input = "",
                    messages = it.messages + ChatMessage("USER", text) + ChatMessage("ASSISTANT", "", isPending = true),
                )
            }
            viewModelScope.launch {
                val request = buildRequest(text)
                when (val result = sendRequest(text, request)) {
                    is AppResult.Success -> {
                        val conversationId = result.data.conversationId
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                conversationId = conversationId,
                                failedInput = null,
                                messages =
                                    it.messages.dropLast(1) +
                                        ChatMessage("ASSISTANT", result.data.reply, sources = result.data.sources),
                            )
                        }
                        cacheExchange(conversationId, text, result.data.reply)
                    }
                    is AppResult.Failure ->
                        _uiState.update { state ->
                            state.copy(
                                isSending = false,
                                error = result.error.toUserMessage(),
                                failedInput = text,
                                messages = state.messages.dropLast(1),
                            )
                        }
                }
            }
        }

        fun retryLast() {
            val failedInput = _uiState.value.failedInput ?: return
            _uiState.update {
                val messages =
                    if (it.messages.lastOrNull()?.role == "USER" && it.messages.last().content == failedInput) {
                        it.messages.dropLast(1)
                    } else {
                        it.messages
                    }
                it.copy(input = failedInput, messages = messages, failedInput = null, error = null)
            }
            send()
        }

        fun retry() {
            if (_uiState.value.failedInput != null) {
                retryLast()
            } else {
                loadHistory()
            }
        }

        fun scorePractice() {
            val state = _uiState.value
            val conversationId = state.conversationId ?: routeConversationId ?: return
            if (routeMode != "conversation-practice" || state.isScoring || state.isSending) return

            _uiState.update { it.copy(isScoring = true, error = null) }
            viewModelScope.launch {
                when (val result = safeApiCall { aiApi.scorePractice(conversationId) }) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(isScoring = false, practiceScore = result.data)
                        }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(isScoring = false, error = result.error.toUserMessage())
                        }
                }
            }
        }

        private suspend fun sendRequest(
            text: String,
            request: AiChatRequestDto,
        ): AppResult<AiChatResponseDto> =
            when (routeMode) {
                "sentence-correction" ->
                    safeApiCall {
                        aiApi.correct(
                            CorrectRequestDto(
                                sentence = text,
                                conversationId = _uiState.value.conversationId ?: routeConversationId,
                            ),
                        )
                    }
                "conversation-practice" -> {
                    val conversationId = _uiState.value.conversationId ?: routeConversationId
                    if (conversationId == null) {
                        safeApiCall { aiApi.startPractice(PracticeStartRequestDto(scenario = text)) }
                    } else {
                        safeApiCall { aiApi.practiceReply(conversationId, PracticeReplyRequestDto(message = text)) }
                    }
                }
                else -> safeApiCall { aiApi.chat(request) }
            }

        private fun buildRequest(text: String): AiChatRequestDto =
            when (routeMode) {
                "grammar-explain" -> AiChatRequestDto(mode = "grammar-explain", message = text, contextGrammarId = routeConversationId)
                "lesson-context" -> AiChatRequestDto(mode = "general", message = text, contextLessonId = routeConversationId)
                "mistakes" -> AiChatRequestDto(mode = "mistakes-review", message = text)
                else ->
                    AiChatRequestDto(
                        conversationId = _uiState.value.conversationId ?: routeConversationId,
                        mode = routeMode,
                        message = text,
                    )
            }

        private fun loadHistory() {
            viewModelScope.launch {
                val conversationId = routeConversationId
                if (conversationId != null && routeMode in HISTORY_MODES) {
                    var loadError: String? = null
                    if (networkMonitor.isOnline.value) {
                        when (val result = safeApiCall { aiApi.messages(conversationId) }) {
                            is AppResult.Success -> {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        conversationId = conversationId,
                                        messages = result.data.map { m -> ChatMessage(m.role, m.content) },
                                        error = null,
                                    )
                                }
                                cacheHistory(conversationId, result.data)
                                return@launch
                            }
                            is AppResult.Failure -> {
                                loadError = result.error.toUserMessage()
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
                            failedInput = null,
                            error = loadError,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }

        private fun observeConnectivity() {
            viewModelScope.launch {
                networkMonitor.isOnline.collect { isOnline ->
                    _uiState.update { state ->
                        state.copy(
                            isOffline = !isOnline,
                            error = if (isOnline && state.error == OFFLINE_MESSAGE) null else state.error,
                        )
                    }
                }
            }
        }

        private suspend fun cacheExchange(
            conversationId: Long,
            userText: String,
            assistantText: String,
        ) {
            try {
                cacheDao.insertAll(
                    listOf(
                        AiMessageCacheEntity(conversationId = conversationId, role = "USER", content = userText),
                        AiMessageCacheEntity(
                            conversationId = conversationId,
                            role = "ASSISTANT",
                            content = assistantText,
                        ),
                    ),
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Timber.w(failure, "Could not cache completed AI exchange")
            }
        }

        private suspend fun cacheHistory(
            conversationId: Long,
            messages: List<com.linguaai.app.data.remote.dto.AiMessageDto>,
        ) {
            try {
                cacheDao.replaceConversation(
                    conversationId,
                    messages.map {
                        AiMessageCacheEntity(
                            conversationId = conversationId,
                            role = it.role,
                            content = it.content,
                        )
                    },
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Timber.w(failure, "Could not refresh cached AI history")
            }
        }

        private companion object {
            val HISTORY_MODES = setOf("conversation", "conversation-practice", "sentence-correction")
            const val OFFLINE_MESSAGE =
                "AI Tutor requires an internet connection. Your learning data is still available offline."
        }
    }

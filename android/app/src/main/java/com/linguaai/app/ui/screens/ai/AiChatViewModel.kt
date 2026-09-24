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
import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.util.ConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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
    val error: AppError? = null,
    val offlineSendNotice: Boolean = false,
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
        private val settingsDataStore: com.linguaai.app.data.datastore.SettingsDataStore? = null,
    ) : ViewModel() {
        private val routeConversationId: Long? = savedStateHandle["conversationId"]
        private val routeMode: String = savedStateHandle["mode"] ?: "general"
        private val routeSeed: String? = savedStateHandle["seed"]

        private val _uiState = MutableStateFlow(AiChatUiState(mode = routeMode, input = routeSeed.orEmpty()))
        val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

        /** The reply currently in flight, so [stop] can abandon it. */
        private var sendJob: Job? = null

        /**
         * Bumped by every send and by [stop]. Cancellation is cooperative, so a
         * completion block can already be past its last suspension point when
         * the learner taps stop; without this check that stale block drops a
         * message bubble it no longer owns.
         */
        private var sendSequence = 0L

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
                        offlineSendNotice = true,
                    )
                }
                return
            }

            _uiState.update {
                it.copy(
                    isSending = true,
                    offlineSendNotice = false,
                    error = null,
                    failedInput = null,
                    input = "",
                    practiceScore = null,
                    messages = it.messages + ChatMessage("USER", text) + ChatMessage("ASSISTANT", "", isPending = true),
                )
            }
            val sequence = ++sendSequence
            sendJob =
                viewModelScope.launch {
                    val request = buildRequest(text)
                    when (val result = sendRequest(text, request)) {
                        is AppResult.Success -> {
                            if (sequence != sendSequence) return@launch
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
                            cacheExchange(conversationId, text, result.data.reply, result.data.sources)
                            settingsDataStore?.recordQuestProgress(com.linguaai.app.domain.model.DailyQuestType.AI_CHAT)
                        }
                        is AppResult.Failure -> {
                            if (sequence != sendSequence) return@launch
                            _uiState.update { state ->
                                state.copy(
                                    isSending = false,
                                    error = result.error,
                                    failedInput = text,
                                    messages = state.messages.dropLast(1),
                                )
                            }
                        }
                    }
                }
        }

        /**
         * Abandon the reply in flight. The learner's own message stays in the
         * transcript because the server did persist it for every mode that
         * reaches [send]; only the empty assistant placeholder is withdrawn.
         */
        fun stop() {
            sendSequence++
            sendJob?.cancel()
            sendJob = null
            _uiState.update { state ->
                if (!state.isSending) return@update state
                state.copy(
                    isSending = false,
                    messages = if (state.messages.lastOrNull()?.isPending == true) state.messages.dropLast(1) else state.messages,
                )
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
                            it.copy(isScoring = false, error = result.error)
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

        private fun buildRequest(text: String): AiChatRequestDto {
            // Every mode must carry the conversation forward. The route argument
            // is a lesson/grammar/quiz id on first turn, so only the id the server
            // handed back is safe to resume with; sending the route id instead made
            // the server open a fresh conversation per message and the tutor lost
            // all memory of the exchange.
            val resumeId = _uiState.value.conversationId
            return when (routeMode) {
                "grammar-explain" ->
                    AiChatRequestDto(
                        conversationId = resumeId,
                        mode = "grammar-explain",
                        message = text,
                        contextGrammarId = routeConversationId,
                    )
                "lesson-context" ->
                    AiChatRequestDto(
                        conversationId = resumeId,
                        mode = "general",
                        message = text,
                        contextLessonId = routeConversationId,
                    )
                "mistakes" ->
                    AiChatRequestDto(
                        conversationId = resumeId,
                        mode = "mistakes-review",
                        message = text,
                    )
                else ->
                    AiChatRequestDto(
                        conversationId = resumeId ?: routeConversationId,
                        mode = routeMode,
                        message = text,
                    )
            }
        }

        private fun loadHistory() {
            viewModelScope.launch {
                val conversationId = routeConversationId
                if (conversationId != null && routeMode in HISTORY_MODES) {
                    var loadError: AppError? = null
                    if (networkMonitor.isOnline.value) {
                        when (val result = safeApiCall { aiApi.messages(conversationId) }) {
                            is AppResult.Success -> {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        conversationId = conversationId,
                                        messages = result.data.map { m -> ChatMessage(m.role, m.content, sources = m.sources) },
                                        error = null,
                                    )
                                }
                                cacheHistory(conversationId, result.data)
                                return@launch
                            }
                            is AppResult.Failure -> {
                                loadError = result.error
                            }
                        }
                    }
                    val cached = cacheDao.byConversation(conversationId)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            conversationId = conversationId,
                            isOffline = !networkMonitor.isOnline.value,
                            messages = cached.map { m -> ChatMessage(m.role, m.content, sources = decodeSources(m.sourcesJson)) },
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
                            offlineSendNotice = if (isOnline) false else state.offlineSendNotice,
                        )
                    }
                }
            }
        }

        private suspend fun cacheExchange(
            conversationId: Long,
            userText: String,
            assistantText: String,
            sources: List<AiSourceDto>,
        ) {
            try {
                cacheDao.insertAll(
                    listOf(
                        AiMessageCacheEntity(conversationId = conversationId, role = "USER", content = userText),
                        AiMessageCacheEntity(
                            conversationId = conversationId,
                            role = "ASSISTANT",
                            content = assistantText,
                            sourcesJson = encodeSources(sources),
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
                            sourcesJson = encodeSources(it.sources),
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
            private val cacheJson = Json { ignoreUnknownKeys = true }

            val HISTORY_MODES = setOf("conversation", "conversation-practice", "sentence-correction")

            /**
             * A citation payload that no longer decodes must not break the
             * transcript, so it degrades to "no sources" like a pre-migration
             * cached row.
             */
            fun decodeSources(raw: String?): List<AiSourceDto> =
                raw?.let {
                    runCatching { cacheJson.decodeFromString<List<AiSourceDto>>(it) }.getOrDefault(emptyList())
                } ?: emptyList()

            fun encodeSources(sources: List<AiSourceDto>): String? =
                sources
                    .takeIf { it.isNotEmpty() }
                    ?.let { runCatching { cacheJson.encodeToString(ListSerializer(AiSourceDto.serializer()), it) }.getOrNull() }
        }
    }

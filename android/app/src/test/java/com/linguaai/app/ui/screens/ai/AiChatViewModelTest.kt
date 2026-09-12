package com.linguaai.app.ui.screens.ai

import androidx.lifecycle.SavedStateHandle
import com.linguaai.app.data.local.dao.AiMessageCacheDao
import com.linguaai.app.data.local.entity.AiMessageCacheEntity
import com.linguaai.app.data.remote.api.AiApi
import com.linguaai.app.data.remote.dto.AiChatRequestDto
import com.linguaai.app.data.remote.dto.AiChatResponseDto
import com.linguaai.app.data.remote.dto.AiConversationDto
import com.linguaai.app.data.remote.dto.AiMessageDto
import com.linguaai.app.data.remote.dto.CorrectRequestDto
import com.linguaai.app.data.remote.dto.ExplainRequestDto
import com.linguaai.app.data.remote.dto.GenerateQuizRequestDto
import com.linguaai.app.data.remote.dto.GeneratedQuizDto
import com.linguaai.app.data.remote.dto.PracticeReplyRequestDto
import com.linguaai.app.data.remote.dto.PracticeScoreDto
import com.linguaai.app.data.remote.dto.PracticeStartRequestDto
import com.linguaai.app.test.MainDispatcherRule
import com.linguaai.app.util.ConnectivityMonitor
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `sentence correction uses the dedicated endpoint`() = runTest(mainDispatcherRule.dispatcher) {
        val api = FakeAiApi()
        val viewModel = viewModel(mode = "sentence-correction", api = api)
        advanceUntilIdle()

        viewModel.onInputChanged("  昨日学校に行きませんでしたから病気でした。  ")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(listOf("correct"), api.calls)
        assertEquals("昨日学校に行きませんでしたから病気でした。", api.correctRequests.single().sentence)

        viewModel.onInputChanged("今日は学校へ行った。")
        viewModel.send()
        advanceUntilIdle()
        assertEquals(listOf("correct", "correct"), api.calls)
        assertEquals(31L, api.correctRequests.last().conversationId)
    }

    @Test
    fun `practice first turn starts a scenario and later turns use reply`() = runTest(mainDispatcherRule.dispatcher) {
        val api = FakeAiApi()
        val viewModel = viewModel(mode = "conversation-practice", api = api)
        advanceUntilIdle()

        viewModel.onInputChanged("ordering lunch politely")
        viewModel.send()
        advanceUntilIdle()
        viewModel.onInputChanged("ラーメンを一つお願いします。")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(listOf("practice-start", "practice-reply"), api.calls)
        assertEquals("ordering lunch politely", api.practiceStarts.single().scenario)
        assertEquals(41L, api.practiceReplies.single().first)
        assertEquals("ラーメンを一つお願いします。", api.practiceReplies.single().second.message)
    }

    @Test
    fun `practice score uses the dedicated endpoint and exposes the result`() = runTest(mainDispatcherRule.dispatcher) {
        val api = FakeAiApi()
        val viewModel = viewModel(mode = "conversation-practice", api = api)
        advanceUntilIdle()

        viewModel.onInputChanged("ordering lunch politely")
        viewModel.send()
        advanceUntilIdle()
        viewModel.scorePractice()
        advanceUntilIdle()

        assertEquals(listOf("practice-start", "practice-score"), api.calls)
        assertEquals(listOf(41L), api.practiceScores)
        assertEquals(84, viewModel.uiState.value.practiceScore?.score)
        assertFalse(viewModel.uiState.value.isScoring)
    }

    @Test
    fun `connectivity changes update the open chat state`() = runTest(mainDispatcherRule.dispatcher) {
        val connectivity = FakeConnectivityMonitor(initiallyOnline = true)
        val viewModel = viewModel(connectivity = connectivity)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOffline)

        connectivity.setOnline(false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isOffline)

        connectivity.setOnline(true)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOffline)
    }

    @Test
    fun `history falls back to Room when the server is unavailable`() = runTest(mainDispatcherRule.dispatcher) {
        val api = FakeAiApi().apply {
            messagesResponse = serverError()
        }
        val cache = FakeAiMessageCacheDao().apply {
            stored += AiMessageCacheEntity(conversationId = 7, role = "USER", content = "cached question")
            stored += AiMessageCacheEntity(conversationId = 7, role = "ASSISTANT", content = "cached answer")
        }

        val viewModel = viewModel(mode = "conversation", conversationId = 7, api = api, cache = cache)
        advanceUntilIdle()

        assertEquals(listOf("cached question", "cached answer"), viewModel.uiState.value.messages.map { it.content })
        assertTrue(viewModel.uiState.value.error != null)
    }

    @Test
    fun `retry replaces the failed turn instead of duplicating the user message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val api = FakeAiApi().apply {
                chatResponses += serverError()
                chatResponses += Response.success(AiChatResponseDto(11, "recovered answer", "general"))
            }
            val viewModel = viewModel(api = api)
            advanceUntilIdle()

            viewModel.onInputChanged("explain ように")
            viewModel.send()
            advanceUntilIdle()
            viewModel.retryLast()
            advanceUntilIdle()

            assertEquals(listOf("USER", "ASSISTANT"), viewModel.uiState.value.messages.map { it.role })
            assertEquals(listOf("explain ように", "recovered answer"), viewModel.uiState.value.messages.map { it.content })
        }

    @Test
    fun `general chat persists one completed exchange in the local cache`() = runTest(mainDispatcherRule.dispatcher) {
        val api = FakeAiApi()
        val cache = FakeAiMessageCacheDao()
        val viewModel = viewModel(api = api, cache = cache)
        advanceUntilIdle()

        viewModel.onInputChanged("hello tutor")
        viewModel.send()
        advanceUntilIdle()

        assertEquals(listOf("chat"), api.calls)
        assertEquals(listOf("USER", "ASSISTANT"), cache.stored.map { it.role })
        assertEquals(listOf("hello tutor", "chat answer"), cache.stored.map { it.content })
    }

    @Test
    fun `reopened correction loads history and reuses its conversation id`() =
        runTest(mainDispatcherRule.dispatcher) {
            val api = FakeAiApi().apply {
                messagesResponse = Response.success(
                    listOf(
                        AiMessageDto(1, "USER", "old sentence"),
                        AiMessageDto(2, "ASSISTANT", "old correction"),
                    ),
                )
            }
            val viewModel = viewModel(mode = "sentence-correction", conversationId = 7, api = api)
            advanceUntilIdle()

            viewModel.onInputChanged("new sentence")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(listOf("messages", "correct"), api.calls)
            assertEquals(7L, api.correctRequests.single().conversationId)
            assertEquals("old sentence", viewModel.uiState.value.messages.first().content)
        }

    @Test
    fun `local cache failure does not strand a successful server turn`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cache = FakeAiMessageCacheDao().apply { failWrites = true }
            val viewModel = viewModel(cache = cache)
            advanceUntilIdle()

            viewModel.onInputChanged("hello tutor")
            viewModel.send()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSending)
            assertEquals(listOf("hello tutor", "chat answer"), viewModel.uiState.value.messages.map { it.content })
        }

    @Test
    fun `send is ignored until requested history finishes loading`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val api = FakeAiApi().apply { messagesGate = gate }
            val viewModel = viewModel(mode = "conversation", conversationId = 7, api = api)
            runCurrent()

            viewModel.onInputChanged("too early")
            viewModel.send()
            runCurrent()

            assertEquals(listOf("messages"), api.calls)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    private fun viewModel(
        mode: String = "general",
        conversationId: Long? = null,
        api: FakeAiApi = FakeAiApi(),
        cache: FakeAiMessageCacheDao = FakeAiMessageCacheDao(),
        connectivity: FakeConnectivityMonitor = FakeConnectivityMonitor(true),
    ): AiChatViewModel {
        val state = mutableMapOf<String, Any?>("mode" to mode)
        conversationId?.let { state["conversationId"] = it }
        return AiChatViewModel(SavedStateHandle(state), api, cache, connectivity)
    }

    private class FakeConnectivityMonitor(initiallyOnline: Boolean) : ConnectivityMonitor {
        private val online = MutableStateFlow(initiallyOnline)
        override val isOnline: StateFlow<Boolean> = online

        fun setOnline(value: Boolean) {
            online.value = value
        }
    }

    private class FakeAiMessageCacheDao : AiMessageCacheDao {
        val stored = mutableListOf<AiMessageCacheEntity>()
        var failWrites = false

        override suspend fun insertAll(messages: List<AiMessageCacheEntity>) {
            if (failWrites) error("cache write failed")
            stored += messages
        }

        override suspend fun byConversation(conversationId: Long): List<AiMessageCacheEntity> =
            stored.filter { it.conversationId == conversationId }

        override suspend fun clearConversation(conversationId: Long) {
            stored.removeAll { it.conversationId == conversationId }
        }

        override suspend fun replaceConversation(
            conversationId: Long,
            messages: List<AiMessageCacheEntity>,
        ) {
            if (failWrites) error("cache write failed")
            clearConversation(conversationId)
            insertAll(messages)
        }

        override suspend fun clearAll() {
            stored.clear()
        }
    }

    private class FakeAiApi : AiApi {
        val calls = mutableListOf<String>()
        val correctRequests = mutableListOf<CorrectRequestDto>()
        val practiceStarts = mutableListOf<PracticeStartRequestDto>()
        val practiceReplies = mutableListOf<Pair<Long, PracticeReplyRequestDto>>()
        val practiceScores = mutableListOf<Long>()
        val chatResponses = ArrayDeque<Response<AiChatResponseDto>>()
        var messagesResponse: Response<List<AiMessageDto>> = Response.success(emptyList())
        var messagesGate: CompletableDeferred<Unit>? = null

        override suspend fun conversations(): Response<List<AiConversationDto>> = Response.success(emptyList())

        override suspend fun messages(conversationId: Long): Response<List<AiMessageDto>> {
            calls += "messages"
            messagesGate?.await()
            return messagesResponse
        }

        override suspend fun chat(body: AiChatRequestDto): Response<AiChatResponseDto> {
            calls += "chat"
            return if (chatResponses.isEmpty()) {
                Response.success(AiChatResponseDto(11, "chat answer", body.mode))
            } else {
                chatResponses.removeFirst()
            }
        }

        override suspend fun explain(body: ExplainRequestDto): Response<AiChatResponseDto> {
            calls += "explain"
            return Response.success(AiChatResponseDto(21, "explanation", "grammar-explain"))
        }

        override suspend fun correct(body: CorrectRequestDto): Response<AiChatResponseDto> {
            calls += "correct"
            correctRequests += body
            return Response.success(AiChatResponseDto(31, "correction", "sentence-correction"))
        }

        override suspend fun generateQuiz(body: GenerateQuizRequestDto): Response<GeneratedQuizDto> =
            Response.success(GeneratedQuizDto(emptyList()))

        override suspend fun startPractice(body: PracticeStartRequestDto): Response<AiChatResponseDto> {
            calls += "practice-start"
            practiceStarts += body
            return Response.success(AiChatResponseDto(41, "practice opening", "conversation-practice"))
        }

        override suspend fun practiceReply(
            conversationId: Long,
            body: PracticeReplyRequestDto,
        ): Response<AiChatResponseDto> {
            calls += "practice-reply"
            practiceReplies += conversationId to body
            return Response.success(AiChatResponseDto(conversationId, "practice answer", "conversation-practice"))
        }

        override suspend fun scorePractice(conversationId: Long): Response<PracticeScoreDto> {
            calls += "practice-score"
            practiceScores += conversationId
            return Response.success(PracticeScoreDto(84, 82, 86, 83))
        }
    }

    private companion object {
        fun <T> serverError(): Response<T> =
            Response.error(
                503,
                """{"error":{"code":"AI_UNAVAILABLE","message":"unavailable","requestId":"test"}}"""
                    .toResponseBody("application/json".toMediaType()),
            )
    }
}

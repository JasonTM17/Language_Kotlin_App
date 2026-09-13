package com.linguaai.app.ui.screens.ai

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AiHomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `quiz request lets the authenticated server profile supply language and level`() =
        runTest(mainDispatcherRule.dispatcher) {
            val api = RecordingAiApi()
            val viewModel = AiHomeViewModel(api)
            advanceUntilIdle()

            viewModel.generateQuiz()
            advanceUntilIdle()

            val request = api.quizRequests.single()
            assertNull(request.languageId)
            assertNull(request.level)
            assertEquals(5, request.count)
            assertFalse(viewModel.uiState.value.isGenerating)
        }

    @Test
    fun `quiz failure is kept separate from conversation loading state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val api =
                RecordingAiApi().apply {
                    quizResponse =
                        Response.error(
                            503,
                            """{"error":{"code":"AI_UNAVAILABLE","message":"unavailable","requestId":"test"}}"""
                                .toResponseBody("application/json".toMediaType()),
                        )
                }
            val viewModel = AiHomeViewModel(api)
            advanceUntilIdle()

            viewModel.generateQuiz()
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.quizError)
            assertNull(viewModel.uiState.value.conversationError)
            assertFalse(viewModel.uiState.value.isGenerating)
        }

    private class RecordingAiApi : AiApi {
        val quizRequests = mutableListOf<GenerateQuizRequestDto>()
        var quizResponse: Response<GeneratedQuizDto> = Response.success(GeneratedQuizDto(emptyList()))

        override suspend fun conversations(): Response<List<AiConversationDto>> = Response.success(emptyList())

        override suspend fun messages(conversationId: Long): Response<List<AiMessageDto>> = Response.success(emptyList())

        override suspend fun chat(body: AiChatRequestDto): Response<AiChatResponseDto> =
            Response.success(AiChatResponseDto(1, "answer", body.mode))

        override suspend fun explain(body: ExplainRequestDto): Response<AiChatResponseDto> =
            Response.success(AiChatResponseDto(1, "answer", "grammar-explain"))

        override suspend fun correct(body: CorrectRequestDto): Response<AiChatResponseDto> =
            Response.success(AiChatResponseDto(1, "answer", "sentence-correction"))

        override suspend fun generateQuiz(body: GenerateQuizRequestDto): Response<GeneratedQuizDto> {
            quizRequests += body
            return quizResponse
        }

        override suspend fun startPractice(body: PracticeStartRequestDto): Response<AiChatResponseDto> =
            Response.success(AiChatResponseDto(1, "opening", "conversation-practice"))

        override suspend fun practiceReply(
            conversationId: Long,
            body: PracticeReplyRequestDto,
        ): Response<AiChatResponseDto> = Response.success(AiChatResponseDto(conversationId, "answer", "conversation-practice"))

        override suspend fun scorePractice(conversationId: Long): Response<PracticeScoreDto> =
            Response.success(PracticeScoreDto(80, 80, 80, 80))
    }
}

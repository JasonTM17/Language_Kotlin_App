package com.linguaai.app.data.remote.api

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
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AiApi {
    @GET("ai/conversations")
    suspend fun conversations(): Response<List<AiConversationDto>>

    @GET("ai/conversations/{id}/messages")
    suspend fun messages(
        @Path("id") conversationId: Long,
    ): Response<List<AiMessageDto>>

    @POST("ai/chat")
    suspend fun chat(
        @Body body: AiChatRequestDto,
    ): Response<AiChatResponseDto>

    @POST("ai/explain")
    suspend fun explain(
        @Body body: ExplainRequestDto,
    ): Response<AiChatResponseDto>

    @POST("ai/correct")
    suspend fun correct(
        @Body body: CorrectRequestDto,
    ): Response<AiChatResponseDto>

    @POST("ai/generate-quiz")
    suspend fun generateQuiz(
        @Body body: GenerateQuizRequestDto,
    ): Response<GeneratedQuizDto>

    @POST("ai/conversation-practice")
    suspend fun startPractice(
        @Body body: PracticeStartRequestDto,
    ): Response<AiChatResponseDto>

    @POST("ai/conversation-practice/{id}/reply")
    suspend fun practiceReply(
        @Path("id") conversationId: Long,
        @Body body: PracticeReplyRequestDto,
    ): Response<AiChatResponseDto>

    @POST("ai/conversation-practice/{id}/score")
    suspend fun scorePractice(
        @Path("id") conversationId: Long,
    ): Response<PracticeScoreDto>
}

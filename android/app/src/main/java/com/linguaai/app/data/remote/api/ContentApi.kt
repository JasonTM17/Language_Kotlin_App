package com.linguaai.app.data.remote.api

import com.linguaai.app.data.remote.dto.GrammarDto
import com.linguaai.app.data.remote.dto.LanguageDto
import com.linguaai.app.data.remote.dto.LessonDto
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.remote.dto.QuizDto
import com.linguaai.app.data.remote.dto.QuizResultDto
import com.linguaai.app.data.remote.dto.QuizSubmissionDto
import com.linguaai.app.data.remote.dto.VocabularyDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ContentApi {
    @GET("languages")
    suspend fun languages(): Response<List<LanguageDto>>

    @GET("lessons")
    suspend fun lessons(
        @Query("languageId") languageId: Long? = null,
        @Query("level") level: String? = null,
        @Query("type") type: String? = null,
    ): Response<List<LessonSummaryDto>>

    @GET("lessons/{id}")
    suspend fun lesson(
        @Path("id") id: Long,
    ): Response<LessonDto>

    @GET("vocabulary")
    suspend fun vocabulary(
        @Query("languageId") languageId: Long? = null,
        @Query("level") level: String? = null,
        @Query("category") category: String? = null,
        @Query("query") query: String? = null,
    ): Response<List<VocabularyDto>>

    @GET("grammar")
    suspend fun grammar(
        @Query("languageId") languageId: Long? = null,
        @Query("level") level: String? = null,
    ): Response<List<GrammarDto>>

    @GET("grammar/{id}")
    suspend fun grammarById(
        @Path("id") id: Long,
    ): Response<GrammarDto>

    @GET("quizzes/{id}")
    suspend fun quiz(
        @Path("id") id: Long,
    ): Response<QuizDto>

    @POST("quizzes/{id}/submit")
    suspend fun submitQuiz(
        @Path("id") id: Long,
        @Body body: QuizSubmissionDto,
    ): Response<QuizResultDto>
}

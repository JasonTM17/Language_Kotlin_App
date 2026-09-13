package com.linguaai.app.data.remote.api

import com.linguaai.app.data.remote.dto.ProgressSummaryDto
import com.linguaai.app.data.remote.dto.RecordProgressEventRequestDto
import com.linguaai.app.data.remote.dto.RecordProgressEventResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ProgressApi {
    @GET("progress")
    suspend fun summary(): Response<ProgressSummaryDto>

    @POST("progress/events")
    suspend fun recordEvent(
        @Body body: RecordProgressEventRequestDto,
    ): Response<RecordProgressEventResponseDto>
}

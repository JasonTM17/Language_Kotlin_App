package com.linguaai.app.data.remote

import com.linguaai.app.data.remote.dto.ApiErrorEnvelopeDto
import com.linguaai.app.domain.model.AppError
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Wraps a Retrofit call into [AppResult], translating transport failures and
 * the server error envelope into domain errors. UI never sees raw exceptions.
 */
suspend fun <T> safeApiCall(call: suspend () -> Response<T>): com.linguaai.app.domain.model.AppResult<T> =
    try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            com.linguaai.app.domain.model.AppResult.Success(body)
        } else if (response.isSuccessful) {
            com.linguaai.app.domain.model.AppResult.Failure(AppError.Unknown)
        } else {
            com.linguaai.app.domain.model.AppResult.Failure(toAppError(response.code(), response.errorBody()?.string()))
        }
    } catch (e: HttpException) {
        com.linguaai.app.domain.model.AppResult.Failure(toAppError(e.code(), null))
    } catch (e: IOException) {
        com.linguaai.app.domain.model.AppResult.Failure(AppError.NetworkUnavailable)
    } catch (e: Exception) {
        com.linguaai.app.domain.model.AppResult.Failure(AppError.Unknown)
    }

private val envelopeJson = Json { ignoreUnknownKeys = true }

fun toAppError(httpCode: Int, rawErrorBody: String?): AppError {
    val serverCode = rawErrorBody?.let { body ->
        runCatching { envelopeJson.decodeFromString<ApiErrorEnvelopeDto>(body).error.code }.getOrNull()
    }
    return when {
        serverCode == "INVALID_CREDENTIALS" || httpCode == 401 -> AppError.Unauthorized
        serverCode == "RATE_LIMITED" || httpCode == 429 -> AppError.RateLimited
        serverCode == "CONFLICT" || httpCode == 409 -> AppError.Conflict
        serverCode == "VALIDATION_ERROR" || httpCode == 400 || httpCode == 422 ->
            AppError.Validation(reason = serverMessage(rawErrorBody))
        httpCode == 403 -> AppError.Forbidden
        httpCode == 404 -> AppError.NotFound
        httpCode in 500..599 || httpCode == 503 -> AppError.ServerError
        else -> AppError.Unknown
    }
}

private fun serverMessage(raw: String?): String? = raw?.let { body ->
    runCatching { envelopeJson.decodeFromString<ApiErrorEnvelopeDto>(body).error.message }.getOrNull()
}

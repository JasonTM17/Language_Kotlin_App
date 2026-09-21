package com.linguaai.app.data.remote

import com.linguaai.app.data.remote.dto.ApiErrorEnvelopeDto
import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber
import java.io.IOException

/**
 * Wraps a Retrofit call into [AppResult], translating transport failures and
 * the server error envelope into domain errors. UI never sees raw exceptions.
 */
suspend fun <T> safeApiCall(call: suspend () -> Response<T>): AppResult<T> =
    try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            AppResult.Success(body)
        } else if (response.isSuccessful) {
            AppResult.Failure(AppError.Unknown)
        } else {
            AppResult.Failure(toAppError(response.code(), response.errorBody()?.string(), response.headers()["Retry-After"]))
        }
    } catch (e: HttpException) {
        AppResult.Failure(toAppError(e.code(), null))
    } catch (e: CancellationException) {
        // A caller that cancels a request (the tutor's stop action) must see the
        // cancellation propagate, not a synthetic failure that then rewrites the
        // transcript the caller has already rolled back.
        throw e
    } catch (e: IOException) {
        // Losing connectivity is expected, not exceptional, so this is logged at
        // debug level: the exception still reaches the log instead of being
        // dropped, but a device that is simply offline does not flood it.
        Timber.d(e, "Network call failed; reporting offline")
        AppResult.Failure(AppError.NetworkUnavailable)
    } catch (e: Exception) {
        // Reaching here is unexpected. Logging the cause is the difference
        // between an operator seeing "Unknown" and seeing what actually failed.
        Timber.w(e, "Unhandled failure while calling the API")
        AppResult.Failure(AppError.Unknown)
    }

private const val CODE_INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
private const val CODE_RATE_LIMITED = "RATE_LIMITED"
private const val CODE_CONFLICT = "CONFLICT"
private const val CODE_VALIDATION_ERROR = "VALIDATION_ERROR"

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409
private const val HTTP_UNPROCESSABLE_ENTITY = 422
private const val HTTP_RATE_LIMITED = 429

/** Every 5xx. `503` used to be listed here separately, which was dead code. */
private val HTTP_SERVER_ERROR = 500..599

private val envelopeJson = Json { ignoreUnknownKeys = true }

/**
 * Maps an HTTP status and the server's error envelope onto [AppError].
 *
 * The branch order *is* the precedence: the first branch whose server code or
 * HTTP status matches wins. [matches] and [isValidation] keep the paired
 * conditions out of the branch bodies so the mapping stays readable as a table.
 */
fun toAppError(
    httpCode: Int,
    rawErrorBody: String?,
    retryAfterHeader: String? = null,
): AppError {
    val serverCode = serverErrorCode(rawErrorBody)
    return when {
        matches(serverCode, httpCode, CODE_INVALID_CREDENTIALS, HTTP_UNAUTHORIZED) -> AppError.Unauthorized
        matches(serverCode, httpCode, CODE_RATE_LIMITED, HTTP_RATE_LIMITED) ->
            AppError.RateLimited(retryAfterSeconds = retryAfterHeader?.trim()?.toLongOrNull())
        matches(serverCode, httpCode, CODE_CONFLICT, HTTP_CONFLICT) -> AppError.Conflict
        isValidation(serverCode, httpCode) -> AppError.Validation(reason = serverMessage(rawErrorBody))
        httpCode == HTTP_FORBIDDEN -> AppError.Forbidden
        httpCode == HTTP_NOT_FOUND -> AppError.NotFound
        httpCode in HTTP_SERVER_ERROR -> AppError.ServerError
        else -> AppError.Unknown
    }
}

private fun matches(
    serverCode: String?,
    httpCode: Int,
    code: String,
    status: Int,
): Boolean = serverCode == code || httpCode == status

private fun isValidation(
    serverCode: String?,
    httpCode: Int,
): Boolean =
    serverCode == CODE_VALIDATION_ERROR ||
        httpCode == HTTP_BAD_REQUEST ||
        httpCode == HTTP_UNPROCESSABLE_ENTITY

private fun serverErrorCode(raw: String?): String? =
    raw?.let { body ->
        runCatching { envelopeJson.decodeFromString<ApiErrorEnvelopeDto>(body).error.code }.getOrNull()
    }

private fun serverMessage(raw: String?): String? =
    raw?.let { body ->
        runCatching { envelopeJson.decodeFromString<ApiErrorEnvelopeDto>(body).error.message }.getOrNull()
    }

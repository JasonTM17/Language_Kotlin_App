package com.linguaai.server.api

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/** Machine-readable API exception carrying an HTTP status and stable code. */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    message: String,
) : RuntimeException(message)

/** Consistent error envelope returned for every failed request. */
@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val requestId: String,
)

@Serializable
data class ErrorResponse(
    val error: ErrorBody,
)

object ErrorCodes {
    const val VALIDATION = "VALIDATION_ERROR"
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val CONFLICT = "CONFLICT"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val AI_UNAVAILABLE = "AI_UNAVAILABLE"
    const val INTERNAL = "INTERNAL_ERROR"
}

package com.linguaai.app.domain.model

/**
 * Domain-level error taxonomy. Data sources translate transport failures into
 * these cases; the UI maps them to friendly messages. Transport exceptions
 * never leak into composables.
 */
sealed interface AppError {
    data object NetworkUnavailable : AppError

    data object Unauthorized : AppError

    data object Forbidden : AppError

    data object NotFound : AppError

    data class Validation(
        val field: String? = null,
        val reason: String? = null,
    ) : AppError

    data object Conflict : AppError

    data object RateLimited : AppError

    data object ServerError : AppError

    data object AiUnavailable : AppError

    data object Unknown : AppError
}

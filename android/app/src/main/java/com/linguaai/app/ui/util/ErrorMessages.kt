package com.linguaai.app.ui.util

import com.linguaai.app.domain.model.AppError

/** Maps domain errors to friendly, actionable UI messages. */
fun AppError.toUserMessage(): String = when (this) {
    AppError.NetworkUnavailable -> "No internet connection. Your learning data is still available offline."
    AppError.Unauthorized -> "Email or password is incorrect."
    AppError.Forbidden -> "You don't have access to this resource."
    AppError.NotFound -> "The requested content was not found."
    is AppError.Validation -> reason ?: "Please check your input."
    AppError.Conflict -> "An account with this email already exists."
    AppError.RateLimited -> "Too many requests. Please wait a moment and try again."
    AppError.ServerError -> "The server is having trouble. Please try again later."
    AppError.AiUnavailable -> "The AI tutor is unavailable right now. Please try again later."
    AppError.Unknown -> "Something went wrong. Please try again."
}

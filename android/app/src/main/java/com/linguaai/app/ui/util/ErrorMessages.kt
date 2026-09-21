package com.linguaai.app.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.linguaai.app.R
import com.linguaai.app.domain.model.AppError

/**
 * The localized copy for a domain error, as a resource id rather than a string.
 *
 * Returning an id is what keeps the ViewModel out of the localization business:
 * resolving text needs resources, and only the composable has them. Anything
 * that stores a resolved String in UI state freezes the language the error was
 * produced in.
 */
@StringRes
fun AppError.messageRes(): Int =
    when (this) {
        AppError.NetworkUnavailable -> R.string.err_network
        AppError.Unauthorized -> R.string.err_unauthorized
        AppError.Forbidden -> R.string.err_forbidden
        AppError.NotFound -> R.string.err_not_found
        is AppError.Validation -> if (reason.isNullOrBlank()) R.string.err_validation else R.string.err_validation_reason
        AppError.Conflict -> R.string.err_conflict
        is AppError.RateLimited -> R.string.err_rate_limited
        AppError.ServerError -> R.string.err_server
        AppError.AiUnavailable -> R.string.err_ai_unavailable
        AppError.Unknown -> R.string.err_unknown
    }

/**
 * User-facing copy for a domain error, resolved where resources exist.
 *
 * `err_validation_reason` takes the server's reason as an argument, so callers
 * must not resolve the id by themselves — a bare `stringResource(id)` on that
 * key renders the literal `%1$s`.
 */
@Composable
fun AppError.asUserMessage(): String {
    val reason = (this as? AppError.Validation)?.reason
    return if (!reason.isNullOrBlank()) {
        stringResource(R.string.err_validation_reason, reason)
    } else {
        stringResource(messageRes())
    }
}

/**
 * Legacy path for the screens that still hold a resolved error String in UI
 * state. New code uses [messageRes] and resolves it in the composable; the
 */
fun AppError.toUserMessage(): String =
    when (this) {
        AppError.NetworkUnavailable -> "No internet connection. Your learning data is still available offline."
        AppError.Unauthorized -> "Email or password is incorrect."
        AppError.Forbidden -> "You don't have access to this resource."
        AppError.NotFound -> "The requested content was not found."
        is AppError.Validation -> reason ?: "Please check your input."
        AppError.Conflict -> "An account with this email already exists."
        is AppError.RateLimited -> "Too many requests. Please wait a moment and try again."
        AppError.ServerError -> "The server is having trouble. Please try again later."
        AppError.AiUnavailable -> "The AI tutor is unavailable right now. Please try again later."
        AppError.Unknown -> "Something went wrong. Please try again."
    }

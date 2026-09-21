package com.linguaai.app.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.linguaai.app.R
import com.linguaai.app.domain.model.AppError

/**
 * The localized copy for a domain error, as a resource id rather than a string.
 *
 * Returning an id is what keeps the ViewModel out of the localization business:
 * resolving text needs resources, and only the composable has them.
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

fun AppError.toUiMessage(): UiMessage = UiMessage(res = messageRes(), arg = (this as? AppError.Validation)?.reason)

/**
 * For state that keeps the [AppError] itself rather than a [UiMessage]. The
 * tutor chat does this because it branches on the variant — a rate limit needs
 * the server's retry grant, which a resource id alone cannot carry.
 *
 * `err_validation_reason` takes the server's reason as an argument, so callers
 * must not resolve [messageRes] by themselves: a bare `stringResource(id)` on
 * that key renders the literal `%1$s`.
 */
@Composable
fun AppError.asUserMessage(): String = toUiMessage().render()

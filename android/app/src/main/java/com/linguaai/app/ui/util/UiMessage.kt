package com.linguaai.app.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A piece of user-facing copy a ViewModel can hold without touching resources.
 *
 * A ViewModel does not own a composition, and storing an already-resolved
 * String freezes the language the value was produced in. Carrying the id plus
 * its optional argument lets the composable resolve it at draw time, so the
 * copy lives in exactly one place: `strings.xml`.
 */
data class UiMessage(
    @param:StringRes val res: Int,
    val arg: String? = null,
)

/** Resolve against the current composition. Call only from a composable. */
@Composable
fun UiMessage.render(): String =
    if (arg.isNullOrBlank()) {
        stringResource(res)
    } else {
        stringResource(res, arg)
    }

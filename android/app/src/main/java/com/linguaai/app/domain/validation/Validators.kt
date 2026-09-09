package com.linguaai.app.domain.validation

/**
 * Pure, side-effect-free input validation shared by use cases and UI hints.
 * Returns null when the value is valid, or a user-facing reason.
 */
object Validators {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Email is required"
        !EMAIL_REGEX.matches(email.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun validatePassword(password: String): String? = when {
        password.isBlank() -> "Password is required"
        password.length < 8 -> "Password must be at least 8 characters"
        else -> null
    }

    fun validateUsername(username: String): String? = when {
        username.isBlank() -> "Username is required"
        username.length < 3 -> "Username must be at least 3 characters"
        else -> null
    }
}

package com.linguaai.app.domain.model

/** Authenticated learner as used by the domain and presentation layers. */
data class User(
    val id: Long,
    val email: String,
    val username: String,
    val avatarUrl: String? = null,
)

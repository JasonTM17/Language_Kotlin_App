package com.linguaai.server.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class LogoutRequest(
    val refreshToken: String,
)

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val username: String,
    val avatarUrl: String? = null,
)

@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
    /** Access token lifetime in seconds, lets clients schedule refresh. */
    val expiresIn: Long,
)

@Serializable
data class AuthResponseDto(
    val user: UserDto,
    val tokens: TokenPairDto,
)

@Serializable
data class RefreshResponseDto(
    val tokens: TokenPairDto,
)

@Serializable
data class ProfileDto(
    val user: UserDto,
    val languageId: Long? = null,
    val level: String? = null,
    val goal: String? = null,
    val dailyGoalMinutes: Int = 10,
    val onboarded: Boolean = false,
)

@Serializable
data class UpdateProfileRequest(
    val languageId: Long? = null,
    val level: String? = null,
    val goal: String? = null,
    val dailyGoalMinutes: Int? = null,
    val onboarded: Boolean? = null,
)

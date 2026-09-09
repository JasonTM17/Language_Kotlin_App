package com.linguaai.app.data.remote.dto

import kotlinx.serialization.Serializable

// ---- requests ----

@Serializable
data class RegisterRequestDto(
    val email: String,
    val username: String,
    val password: String,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshRequestDto(
    val refreshToken: String,
)

@Serializable
data class LogoutRequestDto(
    val refreshToken: String,
)

@Serializable
data class UpdateProfileRequestDto(
    val languageId: Long? = null,
    val level: String? = null,
    val goal: String? = null,
    val dailyGoalMinutes: Int? = null,
    val onboarded: Boolean? = null,
)

// ---- responses ----

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

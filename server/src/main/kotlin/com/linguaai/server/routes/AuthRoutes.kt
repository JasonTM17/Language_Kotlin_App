package com.linguaai.server.routes

import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.api.ErrorResponse
import com.linguaai.server.api.ErrorBody
import com.linguaai.server.api.dto.AuthResponseDto
import com.linguaai.server.api.dto.LoginRequest
import com.linguaai.server.api.dto.LogoutRequest
import com.linguaai.server.api.dto.ProfileDto
import com.linguaai.server.api.dto.RefreshRequest
import com.linguaai.server.api.dto.RefreshResponseDto
import com.linguaai.server.api.dto.RegisterRequest
import com.linguaai.server.api.dto.TokenPairDto
import com.linguaai.server.api.dto.UpdateProfileRequest
import com.linguaai.server.api.dto.UserDto
import com.linguaai.server.config.AppConfig
import com.linguaai.server.plugins.requestId
import com.linguaai.server.repository.AuthRepository
import com.linguaai.server.repository.ContentRepository
import com.linguaai.server.security.JwtTokenService
import com.linguaai.server.security.PasswordHasher
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Authentication + authenticated area (profile, quiz submission).
 * System prompts and secrets stay on the server; the client only ever
 * forwards its own user text.
 */
fun Application.configureAuthRoutes(
    config: AppConfig,
    authRepository: AuthRepository,
) {
    val tokenService = JwtTokenService(config)

    fun issueTokens(userId: Long): TokenPairDto {
        val refreshToken = tokenService.generateRefreshToken()
        authRepository.saveRefreshToken(
            userId = userId,
            tokenHash = tokenService.hashToken(refreshToken),
            familyId = java.util.UUID.randomUUID().toString(),
            expiresAt = LocalDateTime.ofInstant(tokenService.refreshExpiryInstant(), ZoneOffset.UTC),
        )
        return TokenPairDto(
            accessToken = tokenService.generateAccessToken(userId),
            refreshToken = refreshToken,
            expiresIn = tokenService.accessTokenTtlSeconds(),
        )
    }

    routing {
        route("/api/v1/auth") {
            post("/register") {
                val request = call.receive<RegisterRequest>()
                validateRegistration(request)
                val user = authRepository.createUser(
                    email = request.email,
                    username = request.username,
                    passwordHash = PasswordHasher.hash(request.password),
                )
                call.respond(HttpStatusCode.Created, AuthResponseDto(user = user, tokens = issueTokens(user.id)))
            }

            post("/login") {
                val request = call.receive<LoginRequest>()
                val record = authRepository.findUserByEmail(request.email)
                if (record == null || !PasswordHasher.verify(request.password, record.passwordHash)) {
                    // Same error for unknown email and wrong password: no account enumeration.
                    throw ApiException(
                        HttpStatusCode.Unauthorized,
                        ErrorCodes.INVALID_CREDENTIALS,
                        "Email or password is incorrect",
                    )
                }
                val user = UserDto(record.id, record.email, record.username, record.avatarUrl)
                call.respond(AuthResponseDto(user = user, tokens = issueTokens(record.id)))
            }

            post("/refresh") {
                val request = call.receive<RefreshRequest>()
                val hash = tokenService.hashToken(request.refreshToken)
                val stored = authRepository.findRefreshToken(hash)
                    ?: throw ApiException(
                        HttpStatusCode.Unauthorized,
                        ErrorCodes.UNAUTHORIZED,
                        "Invalid refresh token",
                    )
                if (stored.revoked) {
                    // Token replay: someone reused a rotated token -> kill the family.
                    authRepository.revokeFamily(stored.familyId)
                    throw ApiException(
                        HttpStatusCode.Unauthorized,
                        ErrorCodes.UNAUTHORIZED,
                        "Refresh token reuse detected; please log in again",
                    )
                }
                if (stored.expiresAt.isBefore(java.time.LocalDateTime.now())) {
                    throw ApiException(
                        HttpStatusCode.Unauthorized,
                        ErrorCodes.UNAUTHORIZED,
                        "Refresh token expired",
                    )
                }
                authRepository.revokeRefreshToken(stored.id)
                call.respond(RefreshResponseDto(tokens = issueTokens(stored.userId)))
            }

            post("/logout") {
                val request = call.receive<LogoutRequest>()
                val hash = tokenService.hashToken(request.refreshToken)
                authRepository.findRefreshToken(hash)?.let { stored ->
                    authRepository.revokeFamily(stored.familyId)
                }
                call.respond(mapOf("success" to true))
            }
        }
    }
}

private fun validateRegistration(request: RegisterRequest) {
    val emailRegex = Regex("^[A-Za-z0-9+_.\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    if (!emailRegex.matches(request.email.trim())) {
        throw ApiException(HttpStatusCode.UnprocessableEntity, ErrorCodes.VALIDATION, "Invalid email address")
    }
    if (request.username.trim().length < 3) {
        throw ApiException(HttpStatusCode.UnprocessableEntity, ErrorCodes.VALIDATION, "Username too short")
    }
    if (request.password.length < 8) {
        throw ApiException(HttpStatusCode.UnprocessableEntity, ErrorCodes.VALIDATION, "Password must be at least 8 characters")
    }
}

internal fun requireUserId(call: ApplicationCall): Long =
    call.principal<JWTPrincipal>()
        ?.payload
        ?.getClaim(JwtTokenService.CLAIM_USER_ID)
        ?.asString()
        ?.toLongOrNull()
        ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, "Missing principal")

internal fun requireProfile(call: ApplicationCall, authRepository: AuthRepository): ProfileDto =
    authRepository.findProfile(requireUserId(call))
        ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "User not found")

package com.linguaai.server.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.linguaai.server.config.AppConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * Issues and verifies short-lived access JWTs plus opaque refresh tokens.
 * Refresh tokens are stored hashed; a database leak must not leak sessions.
 */
class JwtTokenService(private val config: AppConfig) {

    companion object {
        const val ISSUER = "linguaai-server"
        const val AUDIENCE = "linguaai-app"
        const val CLAIM_USER_ID = "uid"
        private val secureRandom = SecureRandom()
    }

    private val algorithm: Algorithm = Algorithm.HMAC256(config.jwtSecret)

    fun accessTokenVerifier(): JWTVerifier = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()

    fun generateAccessToken(userId: Long): String {
        val expiresAt = Date(System.currentTimeMillis() + config.accessTokenTtlMinutes * 60_000)
        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim(CLAIM_USER_ID, userId.toString())
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    fun accessTokenTtlSeconds(): Long = config.accessTokenTtlMinutes * 60

    /** Opaque, URL-safe refresh token. Only its hash is persisted. */
    fun generateRefreshToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hashToken(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun refreshExpiryInstant(from: Instant = Instant.now()): Instant =
        from.plusSeconds(config.refreshTokenTtlDays * 86_400)
}

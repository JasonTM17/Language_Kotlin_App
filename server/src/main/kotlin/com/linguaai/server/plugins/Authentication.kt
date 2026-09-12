package com.linguaai.server.plugins

import com.linguaai.server.api.ErrorBody
import com.linguaai.server.api.ErrorResponse
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.config.AppConfig
import com.linguaai.server.security.JwtTokenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

/**
 * Installs the JWT authentication plugin.
 *
 * This is cross-cutting infrastructure, not a route. It previously lived inside
 * `configureAuthRoutes`, which meant the auth *plugin* was installed as a side
 * effect of configuring the auth *endpoints* — so anything wanting the first had
 * to depend on the second, and the route file owned a concern it had no business
 * owning.
 */
fun Application.configureAuthentication(config: AppConfig) {
    val tokenService = JwtTokenService(config)

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(tokenService.accessTokenVerifier())
            validate { credential ->
                val userId = credential.payload.getClaim(JwtTokenService.CLAIM_USER_ID)?.asString()
                if (!userId.isNullOrBlank()) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                        ErrorBody(
                            code = ErrorCodes.UNAUTHORIZED,
                            message = "Missing or invalid access token",
                            requestId = call.requestId,
                        ),
                    ),
                )
            }
        }
    }
}

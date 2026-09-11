package com.linguaai.server.routes

import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.api.dto.RecordProgressEventRequest
import com.linguaai.server.repository.ProgressRepository
import com.linguaai.server.security.JwtTokenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Learner progress endpoints.
 *
 * `GET  /api/v1/progress`         aggregated summary (streak, totals, weak topics)
 * `POST /api/v1/progress/events`  record a learning event, idempotent per
 *                                 `clientOperationId`
 *
 * The client never computes the streak; it renders what the server derives.
 */
fun Application.configureProgressRoutes(progressRepository: ProgressRepository = ProgressRepository()) {
    routing {
        authenticate("auth-jwt") {
            route("/api/v1/progress") {
                get {
                    call.respond(progressRepository.summary(call.userId()))
                }

                post("/events") {
                    val request = call.receive<RecordProgressEventRequest>()
                    if (request.clientOperationId.isBlank()) {
                        throw ApiException(
                            HttpStatusCode.BadRequest,
                            ErrorCodes.VALIDATION,
                            "clientOperationId is required",
                        )
                    }
                    if (request.eventType.isBlank()) {
                        throw ApiException(
                            HttpStatusCode.BadRequest,
                            ErrorCodes.VALIDATION,
                            "eventType is required",
                        )
                    }
                    call.respond(progressRepository.recordEvent(call.userId(), request))
                }
            }
        }
    }
}

private fun ApplicationCall.userId(): Long =
    principal<JWTPrincipal>()
        ?.payload
        ?.getClaim(JwtTokenService.CLAIM_USER_ID)
        ?.asString()
        ?.toLongOrNull()
        ?: throw ApiException(
            HttpStatusCode.Unauthorized,
            ErrorCodes.UNAUTHORIZED,
            "Missing principal",
        )

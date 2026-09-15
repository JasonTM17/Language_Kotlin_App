package com.linguaai.server.routes

import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.api.dto.ProgressEventTypes
import com.linguaai.server.api.dto.RecordProgressEventRequest
import com.linguaai.server.api.dto.VocabularyProgressSnapshotDto
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

private const val MAX_MASTERY_LEVEL = 5
private const val MAX_OPERATION_ID_LENGTH = 64

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

                get("/vocabulary") {
                    call.respond(progressRepository.vocabularyProgress(call.userId()))
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
                    if (request.clientOperationId.length > MAX_OPERATION_ID_LENGTH) {
                        throw ApiException(
                            HttpStatusCode.BadRequest,
                            ErrorCodes.VALIDATION,
                            "clientOperationId must be at most $MAX_OPERATION_ID_LENGTH characters",
                        )
                    }
                    if (request.eventType.isBlank()) {
                        throw ApiException(
                            HttpStatusCode.BadRequest,
                            ErrorCodes.VALIDATION,
                            "eventType is required",
                        )
                    }
                    validateVocabularySnapshot(request.eventType, request.refId, request.vocabularyProgress)
                    call.respond(progressRepository.recordEvent(call.userId(), request))
                }
            }
        }
    }
}

private fun validateVocabularySnapshot(
    eventType: String,
    refId: Long?,
    snapshot: VocabularyProgressSnapshotDto?,
) {
    if (eventType == ProgressEventTypes.VOCABULARY_STATE_SYNC && snapshot == null) {
        throw ApiException(
            HttpStatusCode.BadRequest,
            ErrorCodes.VALIDATION,
            "vocabularyProgress is required for vocabulary state-sync events",
        )
    }
    if (snapshot == null) return
    if (eventType !in SNAPSHOT_EVENT_TYPES) {
        throw ApiException(
            HttpStatusCode.BadRequest,
            ErrorCodes.VALIDATION,
            "vocabularyProgress is only valid for flashcard or vocabulary state-sync events",
        )
    }
    if (refId == null || refId <= 0) {
        throw ApiException(
            HttpStatusCode.BadRequest,
            ErrorCodes.VALIDATION,
            "refId is required for vocabulary progress",
        )
    }
    val countersAreNonNegative =
        listOf(snapshot.reviewCount, snapshot.correctCount, snapshot.wrongCount).all { it >= 0 }
    val countersAreConsistent = snapshot.correctCount + snapshot.wrongCount <= snapshot.reviewCount
    if (snapshot.masteryLevel !in 0..MAX_MASTERY_LEVEL || !countersAreNonNegative || !countersAreConsistent) {
        throw ApiException(
            HttpStatusCode.BadRequest,
            ErrorCodes.VALIDATION,
            "vocabularyProgress contains invalid SRS counters",
        )
    }
}

private val SNAPSHOT_EVENT_TYPES =
    setOf(
        ProgressEventTypes.FLASHCARD_REVIEW,
        ProgressEventTypes.VOCABULARY_STATE_SYNC,
    )

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

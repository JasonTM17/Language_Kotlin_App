package com.linguaai.server.routes

import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.api.dto.UpdateProfileRequest
import com.linguaai.server.repository.AuthRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing

/**
 * Profile endpoints.
 *
 * These previously lived in `AuthRoutes`, which meant the file named for
 * authentication also owned the learner's profile. Splitting them keeps each
 * route file answerable for one resource.
 */
fun Application.configureProfileRoutes(authRepository: AuthRepository) {
    routing {
        authenticate("auth-jwt") {
            get("/api/v1/profile") {
                call.respond(requireProfile(call, authRepository))
            }

            put("/api/v1/profile") {
                val userId = requireUserId(call)
                val request = call.receive<UpdateProfileRequest>()
                request.dailyGoalMinutes?.let {
                    if (it !in MIN_DAILY_GOAL..MAX_DAILY_GOAL) {
                        throw ApiException(
                            HttpStatusCode.UnprocessableEntity,
                            ErrorCodes.VALIDATION,
                            "dailyGoalMinutes must be between $MIN_DAILY_GOAL and $MAX_DAILY_GOAL",
                        )
                    }
                }
                call.respond(authRepository.updateProfile(userId, request))
            }
        }
    }
}

/** A daily target below 5 minutes is not a goal; above 240 it is not credible. */
private const val MIN_DAILY_GOAL = 5
private const val MAX_DAILY_GOAL = 240

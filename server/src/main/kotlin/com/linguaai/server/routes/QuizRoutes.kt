package com.linguaai.server.routes

import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.api.dto.QuizSubmissionDto
import com.linguaai.server.repository.ContentRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

/**
 * Quiz submission.
 *
 * Previously lived in `AuthRoutes` — a quiz endpoint in the authentication file.
 */
fun Application.configureQuizRoutes(contentRepository: ContentRepository) {
    routing {
        authenticate("auth-jwt") {
            post("/api/v1/quizzes/{id}/submit") {
                val userId = requireUserId(call)
                val quizId =
                    call.parameters["id"]?.toLongOrNull()
                        ?: throw ApiException(
                            HttpStatusCode.BadRequest,
                            ErrorCodes.VALIDATION,
                            "Invalid quiz id",
                        )
                val submission = call.receive<QuizSubmissionDto>()
                call.respond(contentRepository.submitQuiz(userId, quizId, submission))
            }
        }
    }
}

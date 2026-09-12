package com.linguaai.server.routes

import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.repository.ContentRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Public content endpoints. Quiz submission requires authentication and is
 * registered by the auth module; this file only exposes read paths.
 */
fun Application.configureContentRoutes(repository: ContentRepository) {
    routing {
        route("/api/v1") {
            get("/languages") {
                call.respond(repository.findLanguages())
            }

            get("/lessons") {
                call.respond(
                    repository.findLessons(
                        languageId = call.queryLong("languageId"),
                        level = call.request.queryParameters["level"],
                        type = call.request.queryParameters["type"],
                    ),
                )
            }

            get("/lessons/{id}") {
                call.respond(repository.findLessonById(call.pathLong("id")))
            }

            get("/vocabulary") {
                call.respond(
                    repository.findVocabulary(
                        languageId = call.queryLong("languageId"),
                        level = call.request.queryParameters["level"],
                        category = call.request.queryParameters["category"],
                        query = call.request.queryParameters["query"],
                    ),
                )
            }

            get("/vocabulary/{id}") {
                call.respond(repository.findVocabularyById(call.pathLong("id")))
            }

            get("/grammar") {
                call.respond(
                    repository.findGrammar(
                        languageId = call.queryLong("languageId"),
                        level = call.request.queryParameters["level"],
                    ),
                )
            }

            get("/grammar/{id}") {
                call.respond(repository.findGrammarById(call.pathLong("id")))
            }

            get("/quizzes/{id}") {
                call.respond(repository.findQuizById(call.pathLong("id")))
            }

            get("/categories") {
                call.respond(repository.findVocabulary(null, null, null, null).mapNotNull { it.category }.distinct())
            }
        }
    }
}

private fun ApplicationCall.pathLong(name: String): Long =
    parameters[name]?.toLongOrNull()
        ?: throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.VALIDATION, "Invalid $name parameter")

private fun ApplicationCall.queryLong(name: String): Long? =
    request.queryParameters[name]?.toLongOrNull()
        ?: if (request.queryParameters.contains(name)) {
            throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.VALIDATION, "Invalid $name parameter")
        } else {
            null
        }

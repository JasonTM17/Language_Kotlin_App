package com.linguaai.server.routes

import com.linguaai.server.ai.AiChatRequestDto
import com.linguaai.server.ai.AiChatResponseDto
import com.linguaai.server.ai.AiMessageDto
import com.linguaai.server.ai.AiService
import com.linguaai.server.ai.ConversationDto
import com.linguaai.server.ai.CorrectRequestDto
import com.linguaai.server.ai.ExplainRequestDto
import com.linguaai.server.ai.GeneratedQuizDto
import com.linguaai.server.ai.GenerateQuizRequestDto
import com.linguaai.server.ai.PracticeScoreDto
import com.linguaai.server.ai.PracticeStartRequestDto
import com.linguaai.server.repository.AiRepository
import com.linguaai.server.repository.AuthRepository
import com.linguaai.server.repository.ContentRepository
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

/**
 * Authenticated AI endpoints. Every handler resolves the user principal; the
 * client never picks the system prompt or provider.
 */
fun Application.configureAiRoutes(
    config: com.linguaai.server.config.AppConfig,
    authRepository: AuthRepository,
    contentRepository: ContentRepository,
) {
    val provider: com.linguaai.server.ai.AiProvider = when (config.aiProvider) {
        com.linguaai.server.config.AppConfig.AiProviderKind.MOCK ->
            com.linguaai.server.ai.MockAiProvider(config.aiMockScenario)
        com.linguaai.server.config.AppConfig.AiProviderKind.OPENAI_COMPATIBLE ->
            com.linguaai.server.ai.OpenAiCompatibleProvider(config, aiHttpClient(config))
    }
    val service = com.linguaai.server.ai.AiService(
        config = config,
        provider = provider,
        aiRepository = AiRepository(),
        authRepository = authRepository,
        contentRepository = contentRepository,
        rateLimiter = com.linguaai.server.ai.AiRateLimiter(config.aiRateLimitPerMinute),
    )
    val json = Json { ignoreUnknownKeys = true }

    routing {
        authenticate("auth-jwt") {
            route("/api/v1/ai") {
                get("/conversations") {
                    call.respond(service.conversations(call.userId()))
                }

                get("/conversations/{id}/messages") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw com.linguaai.server.api.ApiException(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            com.linguaai.server.api.ErrorCodes.VALIDATION,
                            "Invalid conversation id",
                        )
                    call.respond(service.messages(call.userId(), id))
                }

                post("/chat") {
                    call.respond(service.chat(call.userId(), call.receive<AiChatRequestDto>()))
                }

                post("/explain") {
                    call.respond(service.explain(call.userId(), call.receive<ExplainRequestDto>()))
                }

                post("/correct") {
                    call.respond(service.correct(call.userId(), call.receive<CorrectRequestDto>()))
                }

                post("/generate-quiz") {
                    call.respond(service.generateQuiz(call.userId(), call.receive<GenerateQuizRequestDto>()))
                }

                post("/conversation-practice") {
                    val request = call.receive<PracticeStartRequestDto>()
                    call.respond(service.startPractice(call.userId(), request))
                }

                post("/conversation-practice/{id}/reply") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw com.linguaai.server.api.ApiException(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            com.linguaai.server.api.ErrorCodes.VALIDATION,
                            "Invalid conversation id",
                        )
                    val body = call.receive<Map<String, String>>()
                    val message = body["message"].orEmpty()
                    call.respond(service.practiceReply(call.userId(), id, message))
                }

                post("/conversation-practice/{id}/score") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw com.linguaai.server.api.ApiException(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            com.linguaai.server.api.ErrorCodes.VALIDATION,
                            "Invalid conversation id",
                        )
                    call.respond(service.scorePractice(call.userId(), id))
                }
            }
        }
    }
}

/** Dedicated outbound client for AI calls: generous timeouts, no auth headers. */
fun aiHttpClient(config: com.linguaai.server.config.AppConfig): io.ktor.client.HttpClient =
    io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000L
            requestTimeoutMillis = config.aiTimeoutSeconds * 1000L
        }
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        }
    }

private fun io.ktor.server.application.ApplicationCall.userId(): Long =
    principal<io.ktor.server.auth.jwt.JWTPrincipal>()
        ?.payload
        ?.getClaim(com.linguaai.server.security.JwtTokenService.CLAIM_USER_ID)
        ?.asString()
        ?.toLongOrNull()
        ?: throw com.linguaai.server.api.ApiException(
            io.ktor.http.HttpStatusCode.Unauthorized,
            com.linguaai.server.api.ErrorCodes.UNAUTHORIZED,
            "Missing principal",
        )

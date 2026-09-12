package com.linguaai.server

import com.linguaai.server.config.AppConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AI gateway tests over the full module with the mock provider:
 * chat + persistence, rate limiting, provider failure mapping, and
 * structured-output validation for generated quizzes.
 */
class AiIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun withApp(
        mockScenario: String? = null,
        rateLimitPerMinute: Int = 20,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val config =
            AppConfig.fromEnv { key ->
                buildMap<String, String> {
                    put("DB_URL", "jdbc:h2:mem:ai_${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
                    put("DB_USER", "sa")
                    put("DB_PASSWORD", "")
                    put("JWT_SECRET", "test-secret-for-integration-tests-only-0123456789")
                    put("RUN_MIGRATIONS", "true")
                    put("AI_RATE_LIMIT_PER_MINUTE", rateLimitPerMinute.toString())
                    mockScenario?.let { put("AI_MOCK_SCENARIO", it) }
                }[key]
            }
        application { module(config) }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndLogin(): String {
        val register =
            client.post("/api/v1/auth/register") {
                setBody("""{"email":"ai-learner@example.com","username":"Learner","password":"ai-fixture-secret"}""")
                header(HttpHeaders.ContentType, "application/json")
            }
        assertEquals(HttpStatusCode.Created, register.status)
        return json
            .parseToJsonElement(register.bodyAsText())
            .jsonObject["tokens"]!!
            .jsonObject["accessToken"]!!
            .jsonPrimitive.content
    }

    @Test
    fun `chat returns a reply and persists history`() =
        withApp {
            val token = registerAndLogin()
            val auth = { r: io.ktor.client.request.HttpRequestBuilder ->
                r.header(HttpHeaders.Authorization, "Bearer $token")
            }

            val chat =
                client.post("/api/v1/ai/chat") {
                    auth(this)
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"~you ni khac ~tameni nhu the nao?"}""")
                }
            assertEquals(HttpStatusCode.OK, chat.status)
            val body = json.parseToJsonElement(chat.bodyAsText()).jsonObject
            val conversationId = body["conversationId"]!!.jsonPrimitive.content

            val history = client.get("/api/v1/ai/conversations/$conversationId/messages") { auth(this) }
            assertEquals(HttpStatusCode.OK, history.status)
            assertTrue(history.bodyAsText().contains("ASSISTANT"))
            assertTrue(history.bodyAsText().contains("USER"))
        }

    @Test
    fun `chat without token is unauthorized`() =
        withApp {
            val chat =
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"hello"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, chat.status)
        }

    @Test
    fun `rate limit returns 429 after quota`() =
        withApp(rateLimitPerMinute = 2) {
            val token = registerAndLogin()
            repeat(2) {
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"question $it"}""")
                }
            }
            val third =
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"one too many"}""")
                }
            assertEquals(HttpStatusCode.TooManyRequests, third.status)
            assertTrue(third.bodyAsText().contains("RATE_LIMITED"))
        }

    @Test
    fun `provider timeout maps to 503 ai unavailable`() =
        withApp(mockScenario = "timeout") {
            val token = registerAndLogin()
            val chat =
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"hello"}""")
                }
            assertEquals(HttpStatusCode.ServiceUnavailable, chat.status)
            assertTrue(chat.bodyAsText().contains("AI_UNAVAILABLE"))
        }

    @Test
    fun `empty provider response maps to 502`() =
        withApp(mockScenario = "empty") {
            val token = registerAndLogin()
            val chat =
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"hello"}""")
                }
            assertEquals(HttpStatusCode.BadGateway, chat.status)
            assertTrue(chat.bodyAsText().contains("AI_UNAVAILABLE"))
        }

    @Test
    fun `generated quiz validates strict payload`() =
        withApp {
            val token = registerAndLogin()
            val quiz =
                client.post("/api/v1/ai/generate-quiz") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"languageId":1,"level":"N3","topic":"grammar","count":3}""")
                }
            assertEquals(HttpStatusCode.OK, quiz.status)
            val body = json.parseToJsonElement(quiz.bodyAsText()).jsonObject
            assertTrue(body.containsKey("questions"))
        }

    @Test
    fun `invalid structured output maps to 502`() =
        withApp(mockScenario = "invalid_json") {
            val token = registerAndLogin()
            val quiz =
                client.post("/api/v1/ai/generate-quiz") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"languageId":1,"level":"N3","count":3}""")
                }
            assertEquals(HttpStatusCode.BadGateway, quiz.status)
            assertTrue(quiz.bodyAsText().contains("AI_UNAVAILABLE"))
        }

    @Test
    fun `sentence correction records a mistake`() =
        withApp {
            val token = registerAndLogin()
            val correct =
                client.post("/api/v1/ai/correct") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"sentence":"kinou gakkou ni ikimasen deshita kara byouki deshita"}""")
                }
            assertEquals(HttpStatusCode.OK, correct.status)
            assertTrue(correct.bodyAsText().contains("conversationId"))
        }
}

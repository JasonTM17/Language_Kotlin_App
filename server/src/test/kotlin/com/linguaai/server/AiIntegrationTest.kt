package com.linguaai.server

import com.linguaai.server.config.AppConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    private suspend fun ApplicationTestBuilder.registerAndLogin(email: String = "ai-learner@example.com"): String {
        val register =
            client.post("/api/v1/auth/register") {
                setBody("""{"email":"$email","username":"Learner","password":"ai-fixture-secret"}""")
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
            // The client's countdown is only as good as this header; without it a
            // learner five seconds from recovery and one a full minute from it see
            // the same open-ended "wait a moment".
            assertNotNull(third.headers[HttpHeaders.RetryAfter])
            assertTrue(third.headers[HttpHeaders.RetryAfter].orEmpty().toLong() > 0L)
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
    fun `generated quiz derives language and level from the learner profile`() =
        withApp(mockScenario = "echo_quiz_prompt") {
            val token = registerAndLogin()
            val profile =
                client.put("/api/v1/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"languageId":2,"level":"A2","goal":"conversation","dailyGoalMinutes":20}""")
                }
            assertEquals(HttpStatusCode.OK, profile.status)

            val quiz =
                client.post("/api/v1/ai/generate-quiz") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"count":3}""")
                }
            assertEquals(HttpStatusCode.OK, quiz.status)
            val prompt =
                json
                    .parseToJsonElement(quiz.bodyAsText())
                    .jsonObject["questions"]!!
                    .jsonArray
                    .first()
                    .jsonObject["prompt"]!!
                    .jsonPrimitive.content
            assertTrue(prompt.contains("A2 learner of English"), prompt)
            assertTrue(!prompt.contains("N3 learner of Japanese"), prompt)
        }

    @Test
    fun `generated quiz without request identity or learner profile is rejected`() =
        withApp {
            val token = registerAndLogin()
            val quiz =
                client.post("/api/v1/ai/generate-quiz") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"count":3}""")
                }
            assertEquals(HttpStatusCode.BadRequest, quiz.status)
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

    @Test
    fun `practice start sends only the requested scenario to the provider`() =
        withApp(mockScenario = "echo_user") {
            val token = registerAndLogin()

            val response =
                client.post("/api/v1/ai/conversation-practice") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"scenario":"ordering lunch politely"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val reply =
                json.parseToJsonElement(response.bodyAsText()).jsonObject
            val conversationId = reply["conversationId"]!!.jsonPrimitive.content
            assertEquals(
                "Let's practice: ordering lunch politely. Please start the conversation.",
                reply["reply"]!!.jsonPrimitive.content,
            )

            val history =
                client.get("/api/v1/ai/conversations/$conversationId/messages") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            val firstMessage =
                json
                    .parseToJsonElement(history.bodyAsText())
                    .jsonArray
                    .first()
                    .jsonObject
            assertEquals("USER", firstMessage["role"]!!.jsonPrimitive.content)
            assertEquals("ordering lunch politely", firstMessage["content"]!!.jsonPrimitive.content)
        }

    @Test
    fun `practice conversation can continue and be scored by the mock provider`() =
        withApp {
            val token = registerAndLogin()
            val start =
                client.post("/api/v1/ai/conversation-practice") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"scenario":"ordering lunch"}""")
                }
            assertEquals(HttpStatusCode.OK, start.status)
            val conversationId =
                json
                    .parseToJsonElement(start.bodyAsText())
                    .jsonObject["conversationId"]!!
                    .jsonPrimitive.content

            val reply =
                client.post("/api/v1/ai/conversation-practice/$conversationId/reply") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"message":"ラーメンを一つお願いします。"}""")
                }
            assertEquals(HttpStatusCode.OK, reply.status)

            val score =
                client.post("/api/v1/ai/conversation-practice/$conversationId/score") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.OK, score.status)
            val scoreBody = json.parseToJsonElement(score.bodyAsText()).jsonObject
            assertTrue(scoreBody["score"]!!.jsonPrimitive.content.toInt() in 0..100)
            assertTrue(scoreBody.containsKey("recommendations"))
        }

    @Test
    fun `blank chat and practice inputs are rejected at the server boundary`() =
        withApp {
            val token = registerAndLogin()

            val blankChat =
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"   "}""")
                }
            assertEquals(HttpStatusCode.BadRequest, blankChat.status)

            val blankScenario =
                client.post("/api/v1/ai/conversation-practice") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"scenario":"   "}""")
                }
            assertEquals(HttpStatusCode.BadRequest, blankScenario.status)

            val start =
                client.post("/api/v1/ai/conversation-practice") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"scenario":"ordering lunch"}""")
                }
            val conversationId =
                json
                    .parseToJsonElement(start.bodyAsText())
                    .jsonObject["conversationId"]!!
                    .jsonPrimitive.content
            val blankReply =
                client.post("/api/v1/ai/conversation-practice/$conversationId/reply") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"message":"   "}""")
                }
            assertEquals(HttpStatusCode.BadRequest, blankReply.status)
        }

    @Test
    fun `provider failure does not leave an empty conversation in history`() =
        withApp(mockScenario = "timeout") {
            val token = registerAndLogin()
            val failed =
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"please explain this"}""")
                }
            assertEquals(HttpStatusCode.ServiceUnavailable, failed.status)

            val conversations =
                client.get("/api/v1/ai/conversations") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.OK, conversations.status)
            assertTrue(json.parseToJsonElement(conversations.bodyAsText()).jsonArray.isEmpty())
        }

    @Test
    fun `another learner cannot continue or score a practice conversation`() =
        withApp {
            val ownerToken = registerAndLogin()
            val otherToken = registerAndLogin("other-ai-learner@example.com")
            val start =
                client.post("/api/v1/ai/conversation-practice") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"scenario":"ordering lunch"}""")
                }
            val conversationId =
                json
                    .parseToJsonElement(start.bodyAsText())
                    .jsonObject["conversationId"]!!
                    .jsonPrimitive.content

            val reply =
                client.post("/api/v1/ai/conversation-practice/$conversationId/reply") {
                    header(HttpHeaders.Authorization, "Bearer $otherToken")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"message":"hello"}""")
                }
            assertEquals(HttpStatusCode.NotFound, reply.status)

            val score =
                client.post("/api/v1/ai/conversation-practice/$conversationId/score") {
                    header(HttpHeaders.Authorization, "Bearer $otherToken")
                }
            assertEquals(HttpStatusCode.NotFound, score.status)
        }

    @Test
    fun `correction history is reused and cannot be claimed by another learner`() =
        withApp {
            val ownerToken = registerAndLogin()
            val otherToken = registerAndLogin("other-correction-learner@example.com")
            val first =
                client.post("/api/v1/ai/correct") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"sentence":"昨日学校へ行きますた。"}""")
                }
            assertEquals(HttpStatusCode.OK, first.status)
            val conversationId =
                json
                    .parseToJsonElement(first.bodyAsText())
                    .jsonObject["conversationId"]!!
                    .jsonPrimitive.content

            val second =
                client.post("/api/v1/ai/correct") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"sentence":"今日学校へ行きますた。","conversationId":$conversationId}""")
                }
            assertEquals(HttpStatusCode.OK, second.status)
            assertEquals(
                conversationId,
                json
                    .parseToJsonElement(second.bodyAsText())
                    .jsonObject["conversationId"]!!
                    .jsonPrimitive.content,
            )

            val history =
                client.get("/api/v1/ai/conversations/$conversationId/messages") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                }
            assertEquals(4, json.parseToJsonElement(history.bodyAsText()).jsonArray.size)

            val crossUser =
                client.post("/api/v1/ai/correct") {
                    header(HttpHeaders.Authorization, "Bearer $otherToken")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"sentence":"claim this","conversationId":$conversationId}""")
                }
            assertEquals(HttpStatusCode.NotFound, crossUser.status)
        }

    @Test
    fun `practice reply and score reject a non-practice conversation`() =
        withApp {
            val token = registerAndLogin()
            val chat =
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"hello"}""")
                }
            val conversationId =
                json
                    .parseToJsonElement(chat.bodyAsText())
                    .jsonObject["conversationId"]!!
                    .jsonPrimitive.content

            val reply =
                client.post("/api/v1/ai/conversation-practice/$conversationId/reply") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"message":"not a role-play"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, reply.status)

            val score =
                client.post("/api/v1/ai/conversation-practice/$conversationId/score") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.BadRequest, score.status)
        }

    /**
     * The practice-score prompt asks the model for mistakes as objects, while
     * the wire DTO declares plain strings. Before normalization a fully
     * compliant reply failed to decode and returned an unrecoverable 502, and
     * the mock hid it by emitting the string form instead.
     */
    /**
     * Citations used to exist only in the live response, so reopening a
     * conversation showed the same answer with its grounding silently gone and
     * the learner could not tell whether it had ever been grounded. This pins
     * the stored round-trip rather than the retrieval that feeds it.
     */
    @Test
    fun `stored citations survive a transcript reload`() =
        withApp {
            // withApp gets a fresh in-memory database per test, so this is the only row.
            registerAndLogin("citations@example.com")
            val userId = 1L
            val repository = com.linguaai.server.repository.AiRepository()
            val conversation =
                repository.createConversation(
                    userId = userId,
                    title = "citation round trip",
                    mode = "general",
                    contextLessonId = null,
                    contextGrammarId = null,
                )
            val stored =
                """[{"title":"Reason clauses","sourceType":"GRAMMAR","sourceId":12,"chunkIndex":0,"level":"N4","score":0.91}]"""

            repository.addExchange(
                conversationId = conversation.id,
                userContent = "why ので",
                assistantContent = "Because the reason precedes the result.",
                sourcesJson = stored,
            )

            val assistant =
                repository.messages(conversation.id).last { it.role == "ASSISTANT" }
            assertEquals(stored, assistant.sources)
            assertNull(
                repository.messages(conversation.id).first { it.role == "USER" }.sources,
            )
        }

    @Test
    fun `practice score accepts the object-shaped mistakes the prompt mandates`() =
        withApp(mockScenario = "prompt_shaped_practice_score") {
            val token = registerAndLogin("prompt-shaped@example.com")
            val auth = { r: io.ktor.client.request.HttpRequestBuilder ->
                r.header(HttpHeaders.Authorization, "Bearer $token")
            }
            val started =
                client.post("/api/v1/ai/conversation-practice") {
                    auth(this)
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"scenario":"ordering food politely"}""")
                }
            val conversationId =
                json.parseToJsonElement(started.bodyAsText()).jsonObject["conversationId"]!!
                    .jsonPrimitive.content
            client.post("/api/v1/ai/conversation-practice/$conversationId/reply") {
                auth(this)
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"message":"ビールをください"}""")
            }

            val score =
                client.post("/api/v1/ai/conversation-practice/$conversationId/score") { auth(this) }

            assertEquals(HttpStatusCode.OK, score.status)
            val mistakes =
                json.parseToJsonElement(score.bodyAsText())
                    .jsonObject["mistakes"]!!
                    .jsonArray
                    .map { it.jsonPrimitive.content }
            assertEquals(1, mistakes.size)
            assertTrue(mistakes.single().contains("ビールをください"))
            assertTrue(mistakes.single().contains("いただけますか"))
        }

    @Test
    fun `practice score rejects provider values outside the accepted range`() =
        withApp(mockScenario = "invalid_practice_score") {
            val token = registerAndLogin()
            val start =
                client.post("/api/v1/ai/conversation-practice") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"scenario":"ordering lunch"}""")
                }
            assertEquals(HttpStatusCode.OK, start.status)
            val conversationId =
                json
                    .parseToJsonElement(start.bodyAsText())
                    .jsonObject["conversationId"]!!
                    .jsonPrimitive.content

            val score =
                client.post("/api/v1/ai/conversation-practice/$conversationId/score") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.BadGateway, score.status)
            assertTrue(score.bodyAsText().contains("AI_UNAVAILABLE"))
        }
}

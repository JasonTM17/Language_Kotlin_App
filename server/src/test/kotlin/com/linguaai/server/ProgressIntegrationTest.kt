package com.linguaai.server

import com.linguaai.server.config.AppConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Progress aggregation and event recording over the full module (Flyway + H2 in
 * MySQL mode).
 *
 * The two properties worth proving here are the ones that are easy to get
 * subtly wrong:
 *
 *  1. **Idempotency** — replaying the same `clientOperationId` must return the
 *     existing event and must not create a second row or double the minutes.
 *     This is what makes offline retry/backoff safe.
 *  2. **Meaningful events only** — an unrecognised event type is stored but must
 *     not extend the streak or add minutes.
 */
class ProgressIntegrationTest {

    // Test fixtures only — these values never correspond to any real system.
    private val fixtureEmail = "progress@example.com"
    private val fixtureSecret = "correct-horse-battery-staple"

    @Serializable
    private data class RegistrationFixture(
        val email: String,
        val username: String,
        val password: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val config = AppConfig.fromEnv { key ->
            mapOf(
                "DB_URL" to "jdbc:h2:mem:progress_${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "DB_USER" to "sa",
                "DB_PASSWORD" to "",
                "JWT_SECRET" to "test-secret-for-integration-tests-only-0123456789",
                "RUN_MIGRATIONS" to "true",
            )[key]
        }
        application { module(config) }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndGetToken(): String {
        val response = client.post("/api/v1/auth/register") {
            setBody(
                json.encodeToString(
                    RegistrationFixture.serializer(),
                    RegistrationFixture(fixtureEmail, "Son", fixtureSecret),
                ),
            )
            header(HttpHeaders.ContentType, "application/json")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject["tokens"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.recordEvent(
        token: String,
        operationId: String,
        eventType: String,
        minutes: Int = 0,
    ): HttpResponse = client.post("/api/v1/progress/events") {
        setBody(
            """{"clientOperationId":"$operationId","eventType":"$eventType","minutes":$minutes}""",
        )
        header(HttpHeaders.ContentType, "application/json")
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private suspend fun ApplicationTestBuilder.progress(token: String) =
        json.parseToJsonElement(
            client.get("/api/v1/progress") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText(),
        ).jsonObject

    @Test
    fun `progress starts empty for a new learner`() = withApp {
        val token = registerAndGetToken()
        val summary = progress(token)

        val streak = summary["streak"]!!.jsonObject
        assertEquals(0, streak["current"]!!.jsonPrimitive.int)
        assertEquals(0, streak["longest"]!!.jsonPrimitive.int)

        val totals = summary["totals"]!!.jsonObject
        assertEquals(0, totals["activeDays"]!!.jsonPrimitive.int)
        assertEquals(0, totals["minutesStudied"]!!.jsonPrimitive.int)
        assertEquals(0, totals["quizAttempts"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a meaningful event opens the streak and adds minutes`() = withApp {
        val token = registerAndGetToken()
        val response = recordEvent(token, "op-1", "FLASHCARD_REVIEW", minutes = 7)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"created\":true"))

        val summary = progress(token)
        assertEquals(1, summary["streak"]!!.jsonObject["current"]!!.jsonPrimitive.int)
        assertEquals(1, summary["totals"]!!.jsonObject["activeDays"]!!.jsonPrimitive.int)
        assertEquals(7, summary["totals"]!!.jsonObject["minutesStudied"]!!.jsonPrimitive.int)
    }

    @Test
    fun `replaying the same operation id is idempotent`() = withApp {
        val token = registerAndGetToken()

        val first = recordEvent(token, "op-dup", "QUIZ_ATTEMPT", minutes = 5)
        val firstBody = json.parseToJsonElement(first.bodyAsText()).jsonObject
        val firstId = firstBody["eventId"]!!.jsonPrimitive.content
        assertTrue(firstBody["created"]!!.jsonPrimitive.content == "true")

        val second = recordEvent(token, "op-dup", "QUIZ_ATTEMPT", minutes = 5)
        val secondBody = json.parseToJsonElement(second.bodyAsText()).jsonObject
        assertEquals(firstId, secondBody["eventId"]!!.jsonPrimitive.content)
        assertFalse(secondBody["created"]!!.jsonPrimitive.content == "true")

        // The replay must not have credited the day twice.
        val summary = progress(token)
        assertEquals(5, summary["totals"]!!.jsonObject["minutesStudied"]!!.jsonPrimitive.int)
        assertEquals(1, summary["totals"]!!.jsonObject["activeDays"]!!.jsonPrimitive.int)
    }

    @Test
    fun `an unrecognised event type does not extend the streak`() = withApp {
        val token = registerAndGetToken()
        val response = recordEvent(token, "op-noise", "SOMETHING_ELSE", minutes = 30)
        assertEquals(HttpStatusCode.OK, response.status)

        val summary = progress(token)
        assertEquals(0, summary["streak"]!!.jsonObject["current"]!!.jsonPrimitive.int)
        assertEquals(0, summary["totals"]!!.jsonObject["activeDays"]!!.jsonPrimitive.int)
        assertEquals(0, summary["totals"]!!.jsonObject["minutesStudied"]!!.jsonPrimitive.int)
    }

    @Test
    fun `progress requires authentication`() = withApp {
        val response = client.get("/api/v1/progress")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `recording requires a client operation id`() = withApp {
        val token = registerAndGetToken()
        val response = client.post("/api/v1/progress/events") {
            setBody("""{"clientOperationId":"","eventType":"QUIZ_ATTEMPT","minutes":1}""")
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

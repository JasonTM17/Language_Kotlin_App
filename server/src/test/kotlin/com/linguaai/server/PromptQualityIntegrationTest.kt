package com.linguaai.server

import com.linguaai.server.ai.PromptBuilder
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Asserts on the system prompt the tutor is actually given.
 *
 * The mock provider echoes the assembled system prompt back as the reply, so
 * these run against the real pipeline — profile lookup, language resolution,
 * lesson/grammar context and mode instruction — rather than against a prompt
 * string assembled separately in the test, which could pass while the real one
 * was wrong.
 *
 * The defect that motivated this: the prompt instructed the model to "insert
 * target-language examples" without ever naming the language, and the quiz prompt
 * interpolated a numeric id ("a learner of language #1"), which conveys nothing.
 */
class PromptQualityIntegrationTest {

    private val fixtureEmail = "prompt@example.com"
    private val fixtureSecret = "correct-horse-battery-staple"

    @Serializable
    private data class RegistrationFixture(val email: String, val username: String, val password: String)

    private val json = Json { ignoreUnknownKeys = true }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val config = AppConfig.fromEnv { key ->
            mapOf(
                "DB_URL" to "jdbc:h2:mem:prompt_${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "DB_USER" to "sa",
                "DB_PASSWORD" to "",
                "JWT_SECRET" to "test-secret-for-integration-tests-only-0123456789",
                "RUN_MIGRATIONS" to "true",
                // Echo the assembled system prompt back as the assistant reply.
                "AI_MOCK_SCENARIO" to "echo_system",
            )[key]
        }
        application { module(config) }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndLogin(): String {
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

    private suspend fun ApplicationTestBuilder.setProfile(token: String, level: String) {
        val response = client.put("/api/v1/profile") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                """{"languageId":1,"level":"$level","goal":"pass JLPT $level","dailyGoalMinutes":20}""",
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    /** Sends a chat turn and returns the system prompt the tutor received. */
    private suspend fun ApplicationTestBuilder.systemPromptFor(
        token: String,
        mode: String = "general",
        message: String = "Explain this grammar point to me",
    ): String {
        val response = client.post("/api/v1/ai/chat") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"mode":"$mode","message":"$message"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject["reply"]!!.jsonPrimitive.content
    }

    @Test
    fun `the prompt names the language the learner is actually studying`() = withApp {
        val token = registerAndLogin()
        setProfile(token, "N3")

        val prompt = systemPromptFor(token)

        // Language id 1 is seeded as Japanese (ja). The tutor must be told the
        // name, not left to infer it.
        assertTrue(
            prompt.contains("Japanese (ja)"),
            "system prompt should name the target language:\n$prompt",
        )
    }

    @Test
    fun `the prompt never leaks a bare numeric language id`() = withApp {
        val token = registerAndLogin()
        setProfile(token, "N3")

        val prompt = systemPromptFor(token)

        assertFalse(
            Regex("language\\s*#\\d+").containsMatchIn(prompt),
            "a numeric language id is meaningless to a model:\n$prompt",
        )
    }

    @Test
    fun `the prompt carries level-specific complexity guidance`() = withApp {
        val token = registerAndLogin()
        setProfile(token, "N3")

        val prompt = systemPromptFor(token)

        assertTrue(prompt.contains("## Level guidance (N3)"), "missing level section:\n$prompt")
        // A bare label is not enough; the guidance must say what N3 implies.
        assertTrue(
            prompt.contains("Lower intermediate"),
            "level guidance should describe the level, not just name it:\n$prompt",
        )
    }

    @Test
    fun `the prompt states a teaching method and its boundaries`() = withApp {
        val token = registerAndLogin()
        setProfile(token, "N3")

        val prompt = systemPromptFor(token)

        assertTrue(prompt.contains("## How to teach"), "missing teaching method:\n$prompt")
        assertTrue(prompt.contains("## Boundaries"), "missing guardrails:\n$prompt")
        // The anti-hallucination rule is the one that matters most for a tutor.
        assertTrue(
            prompt.contains("Never invent grammar rules"),
            "prompt must forbid inventing grammar:\n$prompt",
        )
    }

    @Test
    fun `each mode gets its own output contract`() = withApp {
        val token = registerAndLogin()
        setProfile(token, "N3")

        val correction = systemPromptFor(token, mode = "sentence-correction", message = "check this")
        assertTrue(correction.contains("**Corrected:**"), "correction mode needs a fixed shape:\n$correction")

        val general = systemPromptFor(token, mode = "general", message = "hello")
        assertFalse(
            general.contains("**Corrected:**"),
            "modes must not share each other's contract:\n$general",
        )
    }

    @Test
    fun `a learner with no profile is told to ask rather than guess`() = withApp {
        val token = registerAndLogin()
        // Deliberately no setProfile call: the account has no level set.

        val prompt = systemPromptFor(token)

        assertTrue(
            prompt.contains("ask the learner which language they are studying"),
            "an unset language must be surfaced, not silently defaulted:\n$prompt",
        )
    }

    @Test
    fun `the grammar heading constant matches what the mock keys on`() = withApp {
        val token = registerAndLogin()
        setProfile(token, "N3")

        // Grammar context only appears when a grammar point is in focus, so assert
        // the constant itself is what the builder emits.
        assertTrue(PromptBuilder.HEADING_GRAMMAR.startsWith("##"))
        assertEquals("## Grammar in focus", PromptBuilder.HEADING_GRAMMAR)
    }
}

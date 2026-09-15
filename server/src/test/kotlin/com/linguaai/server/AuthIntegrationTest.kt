package com.linguaai.server

import com.linguaai.server.config.AppConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end authentication flow tests over the full module (Flyway + H2 in
 * MySQL mode). Each test gets an isolated in-memory database. Request bodies
 * are encoded from typed fixtures so the tests mirror the real DTO contracts.
 */
class AuthIntegrationTest {
    // Test fixtures only — these values never correspond to any real system.
    private val fixtureEmail = "son@example.com"
    private val fixtureSecret = "correct-horse-battery-staple"
    private val wrongSecret = "totally-different-value"

    @Serializable
    private data class RegistrationFixture(
        val email: String,
        val username: String,
        val password: String,
    )

    @Serializable
    private data class LoginFixture(
        val email: String,
        val password: String,
    )

    @Serializable
    private data class RefreshFixture(
        val refreshToken: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            val config =
                AppConfig.fromEnv { key ->
                    mapOf(
                        "DB_URL" to "jdbc:h2:mem:auth_${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                        "DB_USER" to "sa",
                        "DB_PASSWORD" to "",
                        "JWT_SECRET" to "test-secret-for-integration-tests-only-0123456789",
                        "RUN_MIGRATIONS" to "true",
                    )[key]
                }
            application { module(config) }
            block()
        }

    private suspend fun ApplicationTestBuilder.postJson(
        path: String,
        body: String,
    ): HttpResponse =
        client.post(path) {
            setBody(body)
            header(HttpHeaders.ContentType, "application/json")
        }

    private suspend fun ApplicationTestBuilder.register(email: String = fixtureEmail): HttpResponse =
        postJson(
            "/api/v1/auth/register",
            json.encodeToString(
                RegistrationFixture.serializer(),
                RegistrationFixture(email = email, username = "Son", password = fixtureSecret),
            ),
        )

    private suspend fun ApplicationTestBuilder.registerAndLogin(email: String = fixtureEmail): JsonObject {
        val registerResponse = register(email)
        assertEquals(HttpStatusCode.Created, registerResponse.status)
        return json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
    }

    @Test
    fun `health endpoint responds ok`() =
        withApp {
            val response = client.get("/api/v1/health")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("ok"))
        }

    @Test
    fun `register returns tokens and profile starts empty`() =
        withApp {
            val body = registerAndLogin()
            val user = body["user"]!!.jsonObject
            val tokens = body["tokens"]!!.jsonObject
            assertEquals(fixtureEmail, user["email"]!!.jsonPrimitive.content)
            assertTrue(tokens["accessToken"]!!.jsonPrimitive.content.isNotBlank())

            val access = tokens["accessToken"]!!.jsonPrimitive.content
            val profile = client.get("/api/v1/profile") { header(HttpHeaders.Authorization, "Bearer $access") }
            assertEquals(HttpStatusCode.OK, profile.status)
            assertTrue(profile.bodyAsText().contains("\"onboarded\":false"))
        }

    @Test
    fun `register duplicate email returns conflict`() =
        withApp {
            registerAndLogin()
            val second = register()
            assertEquals(HttpStatusCode.Conflict, second.status)
            assertTrue(second.bodyAsText().contains("CONFLICT"))
        }

    @Test
    fun `login with wrong secret returns invalid credentials`() =
        withApp {
            registerAndLogin()
            val response =
                postJson(
                    "/api/v1/auth/login",
                    json.encodeToString(
                        LoginFixture.serializer(),
                        LoginFixture(email = fixtureEmail, password = wrongSecret),
                    ),
                )
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(response.bodyAsText().contains("INVALID_CREDENTIALS"))
        }

    @Test
    fun `refresh rotates token and rejects reuse`() =
        withApp {
            val body = registerAndLogin()
            val refreshToken = body["tokens"]!!.jsonObject["refreshToken"]!!.jsonPrimitive.content

            val firstRefresh =
                postJson(
                    "/api/v1/auth/refresh",
                    json.encodeToString(RefreshFixture.serializer(), RefreshFixture(refreshToken)),
                )
            assertEquals(HttpStatusCode.OK, firstRefresh.status)

            val reuse =
                postJson(
                    "/api/v1/auth/refresh",
                    json.encodeToString(RefreshFixture.serializer(), RefreshFixture(refreshToken)),
                )
            assertEquals(HttpStatusCode.Unauthorized, reuse.status)
        }

    @Test
    fun `protected endpoints reject missing token`() =
        withApp {
            val response = client.get("/api/v1/profile")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `profile update and quiz submit flow`() =
        withApp {
            val body = registerAndLogin(email = "flow@example.com")
            val access = body["tokens"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content

            val updated =
                client.put("/api/v1/profile") {
                    header(HttpHeaders.Authorization, "Bearer $access")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(
                        """{"languageId":1,"level":"N3","goal":"JLPT","dailyGoalMinutes":20,"onboarded":true}""",
                    )
                }
            assertEquals(HttpStatusCode.OK, updated.status)
            assertTrue(updated.bodyAsText().contains("\"onboarded\":true"))

            val quiz = client.get("/api/v1/quizzes/1")
            assertEquals(HttpStatusCode.OK, quiz.status)

            val submit =
                client.post("/api/v1/quizzes/1/submit") {
                    header(HttpHeaders.Authorization, "Bearer $access")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(
                        """{"answers":[
                    {"questionId":1,"answer":"Môi trường"},
                    {"questionId":2,"answer":"かぞく"},
                    {"questionId":3,"answer":"Giáo viên"},
                    {"questionId":4,"answer":"水"},
                    {"questionId":5,"answer":"Sân bay"}],
                    "durationSeconds":90}""",
                    )
                }
            assertEquals(HttpStatusCode.OK, submit.status)
            assertTrue(submit.bodyAsText().contains("\"score\":4"))
        }

    @Test
    fun `all seeded quizzes expose valid option arrays`() =
        withApp {
            (1L..6L).forEach { quizId ->
                val response = client.get("/api/v1/quizzes/$quizId")
                assertEquals(HttpStatusCode.OK, response.status)
                val questions = json.parseToJsonElement(response.bodyAsText()).jsonObject["questions"]!!.jsonArray
                assertEquals(5, questions.size)
                assertTrue(
                    questions.all { question ->
                        question.jsonObject["options"]!!.jsonArray.size == 4
                    },
                )
            }
        }

    @Test
    fun `vocabulary search filters by query`() =
        withApp {
            val response = client.get("/api/v1/vocabulary?languageId=1&query=環境")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Môi trường"))
        }

    @Test
    fun `expanded multilingual catalog exposes every language and level`() =
        withApp {
            val languages = client.get("/api/v1/languages")
            assertEquals(HttpStatusCode.OK, languages.status)
            val languageCodes =
                json
                    .parseToJsonElement(languages.bodyAsText())
                    .jsonArray
                    .map { it.jsonObject["code"]!!.jsonPrimitive.content }
                    .toSet()
            assertEquals(
                setOf("ja", "en", "ko", "es", "fr", "zh", "de", "it", "pt", "ru", "ar", "hi", "id", "th", "tr", "vi", "nl"),
                languageCodes,
            )

            (1L..17L).forEach { languageId ->
                val response = client.get("/api/v1/vocabulary?languageId=$languageId")
                assertEquals(HttpStatusCode.OK, response.status)
                val words = json.parseToJsonElement(response.bodyAsText()).jsonArray
                assertTrue(words.size <= 100)
                assertEquals(words.size, words.map { it.jsonObject["word"]!!.jsonPrimitive.content }.toSet().size)
            }

            val chineseSearch = client.get("/api/v1/vocabulary?languageId=6&query=天空")
            assertEquals(HttpStatusCode.OK, chineseSearch.status)
            assertTrue(chineseSearch.bodyAsText().contains("Bầu trời"))

            val hskTwo = client.get("/api/v1/vocabulary?languageId=6&level=HSK2")
            assertEquals(HttpStatusCode.OK, hskTwo.status)
            val hskTwoWords = json.parseToJsonElement(hskTwo.bodyAsText()).jsonArray
            assertEquals(30, hskTwoWords.size)
            assertTrue(hskTwoWords.all { it.jsonObject["level"]!!.jsonPrimitive.content == "HSK2" })
        }

    @Test
    fun `vocabulary endpoint keeps large catalog reads bounded`() =
        withApp {
            val firstPage = client.get("/api/v1/vocabulary?languageId=1&limit=2&offset=1")
            assertEquals(HttpStatusCode.OK, firstPage.status)
            val page = json.parseToJsonElement(firstPage.bodyAsText()).jsonArray
            assertEquals(2, page.size)
            assertEquals(2, page[0].jsonObject["id"]!!.jsonPrimitive.long)
            assertEquals(3, page[1].jsonObject["id"]!!.jsonPrimitive.long)

            val emptyPage = client.get("/api/v1/vocabulary?limit=0")
            assertEquals(HttpStatusCode.OK, emptyPage.status)
            assertEquals(0, json.parseToJsonElement(emptyPage.bodyAsText()).jsonArray.size)

            assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/vocabulary?limit=201").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/vocabulary?offset=-1").status)

            val categories = client.get("/api/v1/categories")
            assertEquals(HttpStatusCode.OK, categories.status)
            assertTrue(json.parseToJsonElement(categories.bodyAsText()).jsonArray.isNotEmpty())
        }
}

package com.linguaai.server.ops

import com.linguaai.server.config.AppConfig
import com.linguaai.server.module
import io.ktor.client.request.delete
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scale-seeding and teardown on H2: deterministic generation, stats deltas,
 * and purge reversibility. The big seed volumes themselves are exercised by
 * the live E2E harness against MySQL + Qdrant, not here.
 */
class OpsSeedIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val ops = mapOf("X-Ops-Token" to AppConfig.DEV_OPS_TOKEN)

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            val config =
                AppConfig.fromEnv { key ->
                    buildMap<String, String> {
                        put("DB_URL", "jdbc:h2:mem:ops_${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
                        put("DB_USER", "sa")
                        put("DB_PASSWORD", "")
                        put("JWT_SECRET", "test-secret-for-integration-tests-only-0123456789")
                        put("RUN_MIGRATIONS", "true")
                    }[key]
                }
            application { module(config) }
            block()
        }

    private suspend fun ApplicationTestBuilder.stats(): Pair<Long, Long> {
        val response =
            client.get("/api/v1/ops/stats") {
                ops.forEach { (name, value) -> header(name, value) }
            }
        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return Pair(
            payload["vocabularies"]!!.jsonPrimitive.content.toLong(),
            payload["knowledgeChunks"]!!.jsonPrimitive.content.toLong(),
        )
    }

    private suspend fun ApplicationTestBuilder.seed(
        words: Int,
        grammar: Int,
        lessons: Int,
    ): HttpStatusCode =
        client
            .post("/api/v1/ops/seed/scale?wordsPerLanguage=$words&grammarPerLanguage=$grammar&lessonsPerLanguage=$lessons") {
                ops.forEach { (name, value) -> header(name, value) }
            }.status

    @Test
    fun `seed scale adds deterministic rows and purge reverts them`() =
        withApp {
            val (vocabBefore, _) = stats()

            val seedResponse =
                client.post("/api/v1/ops/seed/scale?wordsPerLanguage=3&grammarPerLanguage=2&lessonsPerLanguage=1") {
                    ops.forEach { (name, value) -> header(name, value) }
                }
            assertEquals(HttpStatusCode.OK, seedResponse.status)
            val report = json.parseToJsonElement(seedResponse.bodyAsText()).jsonObject
            val languages = report["languagesCovered"]!!.jsonPrimitive.content.toLong()
            assertTrue(languages >= 2, "the seeded catalogue has multiple languages")
            assertEquals(languages * 3, report["vocabulariesInserted"]!!.jsonPrimitive.content.toLong())
            assertEquals(languages * 1, report["lessonsInserted"]!!.jsonPrimitive.content.toLong())

            val (vocabAfter, _) = stats()
            assertEquals(vocabBefore + languages * 3, vocabAfter, "stats must reflect the seeded rows")

            reindex()
            val purgeReport = purge()
            assertEquals(languages * 3, purgeReport.vocabulariesDeleted)
            assertTrue(purgeReport.chunksDeleted > 0, "purge must remove chunks seeded rows produced")

            val (vocabRestored, _) = stats()
            assertEquals(vocabBefore, vocabRestored, "purge must revert the corpus to its baseline")
        }

    @Test
    fun `seeded vocabulary becomes retrievable and disappears after purge`() =
        withApp {
            assertEquals(HttpStatusCode.OK, seed(words = 3, grammar = 1, lessons = 1))
            reindex()
            val hitBefore = knowledgeSearch("synthetic")
            assertTrue(hitBefore.contains("VOCABULARY"), "a seeded term must be retrievable before purge")

            purge()
            reindex()
            val hitAfter = knowledgeSearch("synthetic")
            assertTrue(!hitAfter.contains("VOCABULARY"), "purged terms must leave the index")
        }

    @Test
    fun `seed requests beyond the caps are rejected`() =
        withApp {
            assertEquals(HttpStatusCode.BadRequest, seed(words = 50_001, grammar = 0, lessons = 0))
            assertEquals(HttpStatusCode.BadRequest, seed(words = 0, grammar = 0, lessons = 5_001))
            assertEquals(HttpStatusCode.BadRequest, seed(words = -1, grammar = 0, lessons = 0))
        }

    @Test
    fun `stats requires the ops token`() =
        withApp {
            val response = client.get("/api/v1/ops/stats")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    private suspend fun ApplicationTestBuilder.reindex() {
        val response =
            client.post("/api/v1/ops/rag/reindex") {
                ops.forEach { (name, value) -> header(name, value) }
            }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private suspend fun ApplicationTestBuilder.purge(): PurgeParsed {
        val response =
            client.delete("/api/v1/ops/seed") {
                ops.forEach { (name, value) -> header(name, value) }
            }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(PurgeParsed.serializer(), response.bodyAsText())
    }

    @kotlinx.serialization.Serializable
    private data class PurgeParsed(
        val vocabulariesDeleted: Long,
        val grammarDeleted: Long,
        val lessonsDeleted: Long,
        val chunksDeleted: Long,
    )

    private suspend fun ApplicationTestBuilder.knowledgeSearch(query: String): String {
        val token = registerAndLogin()
        setProfileJapanese(token)
        val response =
            client.get("/api/v1/ai/knowledge/search?q=$query") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.bodyAsText()
    }

    private suspend fun ApplicationTestBuilder.registerAndLogin(): String {
        val email = "ops-seed-${UUID.randomUUID()}@example.com"
        val register =
            client.post("/api/v1/auth/register") {
                setBody("""{"email":"$email","username":"Learner","password":"ops-fixture-secret"}""")
                header(HttpHeaders.ContentType, "application/json")
            }
        assertEquals(HttpStatusCode.Created, register.status)
        return json
            .parseToJsonElement(register.bodyAsText())
            .jsonObject["tokens"]!!
            .jsonObject["accessToken"]!!
            .jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.setProfileJapanese(token: String) {
        val response =
            client.put("/api/v1/profile") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"languageId":1,"level":"N3","goal":"fluency","dailyGoalMinutes":20}""")
            }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}

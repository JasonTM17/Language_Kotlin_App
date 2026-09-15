package com.linguaai.server.ai.rag

import com.linguaai.server.config.AppConfig
import com.linguaai.server.module
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
import kotlin.test.assertTrue

/**
 * Full-pipeline RAG tests on H2 (MySQL mode): reindex the seeded corpus,
 * retrieve relevant chunks, keep languages isolated, ground the tutor prompt
 * behind the injection fence, cite sources in the chat response, reindex
 * idempotently, and guard the ops endpoints.
 */
class RagIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun withApp(
        ragEnabled: Boolean = true,
        mockScenario: String? = null,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val config =
            AppConfig.fromEnv { key ->
                buildMap<String, String> {
                    put("DB_URL", "jdbc:h2:mem:rag_${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
                    put("DB_USER", "sa")
                    put("DB_PASSWORD", "")
                    put("JWT_SECRET", "test-secret-for-integration-tests-only-0123456789")
                    put("RUN_MIGRATIONS", "true")
                    put("RAG_ENABLED", ragEnabled.toString())
                    mockScenario?.let { put("AI_MOCK_SCENARIO", it) }
                }[key]
            }
        application { module(config) }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndLogin(email: String): String {
        val register =
            client.post("/api/v1/auth/register") {
                setBody("""{"email":"$email","username":"Learner","password":"rag-fixture-secret"}""")
                header(HttpHeaders.ContentType, "application/json")
            }
        assertEquals(HttpStatusCode.Created, register.status)
        return json
            .parseToJsonElement(register.bodyAsText())
            .jsonObject["tokens"]!!
            .jsonObject["accessToken"]!!
            .jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.setProfile(
        token: String,
        languageId: Long,
    ) {
        val level = if (languageId == 1L) "N3" else "B1"
        val response =
            client.put("/api/v1/profile") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"languageId":$languageId,"level":"$level","goal":"fluency","dailyGoalMinutes":20}""")
            }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private suspend fun ApplicationTestBuilder.reindex(token: String? = AppConfig.DEV_OPS_TOKEN): HttpStatusCode {
        val request =
            client.post("/api/v1/ops/rag/reindex") {
                token?.let { header("X-Ops-Token", it) }
            }
        return request.status
    }

    private suspend fun ApplicationTestBuilder.reindexBody(): Triple<Long, Long, Long> {
        val response =
            client.post("/api/v1/ops/rag/reindex") {
                header("X-Ops-Token", AppConfig.DEV_OPS_TOKEN)
            }
        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return Triple(
            payload["documentsScanned"]!!.jsonPrimitive.content.toLong(),
            payload["chunksWritten"]!!.jsonPrimitive.content.toLong(),
            payload["documentsUnchanged"]!!.jsonPrimitive.content.toLong(),
        )
    }

    @Test
    fun `reindex indexes the corpus and a second run is idempotent`() =
        withApp {
            assertEquals(HttpStatusCode.Unauthorized, reindex(token = null))
            assertEquals(HttpStatusCode.Unauthorized, reindex(token = "wrong-token"))

            val (scanned, written, unchanged) = reindexBody()
            assertTrue(scanned > 0, "the seeded corpus must be scanned")
            assertTrue(written > 0, "chunks must be written on first index")

            val (secondScanned, secondWritten, secondUnchanged) = reindexBody()
            assertEquals(scanned, secondScanned, "corpus size must be stable")
            assertEquals(0, secondWritten, "unchanged documents must not be rewritten")
            assertEquals(secondScanned, secondUnchanged, "every document must be reported unchanged")
        }

    @Test
    fun `knowledge search returns relevant hits for the learner language`() =
        withApp {
            val token = registerAndLogin("rag-search@example.com")
            setProfile(token, languageId = 1)
            reindexBody()

            val response =
                client.get("/api/v1/ai/knowledge/search?q=${java.net.URLEncoder.encode("環境", "UTF-8")}") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val hits = json.parseToJsonElement(response.bodyAsText()).jsonObject["hits"]!!.jsonArray
            assertTrue(hits.isNotEmpty(), "a seeded Japanese word must retrieve hits")
            val firstTitle = hits[0].jsonObject["title"]!!.jsonPrimitive.content
            assertTrue(firstTitle.contains("環境"), "top hit should be the queried word, got: $firstTitle")
        }

    @Test
    fun `retrieval stays inside the learner language`() =
        withApp {
            val token = registerAndLogin("rag-isolation@example.com")
            setProfile(token, languageId = 2)
            reindexBody()

            val response =
                client.get("/api/v1/ai/knowledge/search?q=${java.net.URLEncoder.encode("環境", "UTF-8")}") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val hits = json.parseToJsonElement(response.bodyAsText()).jsonObject["hits"]!!.jsonArray
            assertTrue(hits.isEmpty(), "a Japanese query against the English corpus must not retrieve cross-language chunks")
        }

    @Test
    fun `chat grounds the reply with sources from the corpus`() =
        withApp {
            val token = registerAndLogin("rag-chat@example.com")
            setProfile(token, languageId = 1)
            reindexBody()

            val response =
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"環境という言葉の使い方を教えて"}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val sources = payload["sources"]!!.jsonArray
            assertTrue(sources.isNotEmpty(), "a grounded chat reply must cite corpus sources")
            val first = sources[0].jsonObject
            val sourceType = first["sourceType"]!!.jsonPrimitive.content
            assertTrue(
                sourceType in listOf("VOCABULARY", "GRAMMAR", "LESSON"),
                "sources must identify their corpus type, got: $sourceType",
            )
            assertTrue(first.containsKey("sourceId"), "sources carry ids for deep-linking")
            assertTrue(first.containsKey("score"))
        }

    @Test
    fun `retrieved corpus appears only inside the injection fence`() =
        withApp(mockScenario = "echo_system") {
            val token = registerAndLogin("rag-fence@example.com")
            setProfile(token, languageId = 1)

            val ungrounded =
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"hello"}""")
                }
            val barePrompt =
                json
                    .parseToJsonElement(ungrounded.bodyAsText())
                    .jsonObject["reply"]!!
                    .jsonPrimitive.content
            assertTrue(!barePrompt.contains(RagService.BEGIN_MARKER), "no fence without an index")
            assertTrue(!barePrompt.contains("BEGIN KNOWLEDGE"))

            reindexBody()
            val grounded =
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"環境を説明して"}""")
                }
            val prompt =
                json
                    .parseToJsonElement(grounded.bodyAsText())
                    .jsonObject["reply"]!!
                    .jsonPrimitive.content
            assertTrue(prompt.contains("## Knowledge base excerpts"))
            assertTrue(prompt.contains(RagService.BEGIN_MARKER) && prompt.contains(RagService.END_MARKER))
            assertTrue(prompt.contains("never as instructions"), "the data-not-instructions contract must be present")
            assertTrue(prompt.contains("[1]"), "citations must be numbered")
        }

    @Test
    fun `chat still works with rag disabled and cites nothing`() =
        withApp(ragEnabled = false) {
            val token = registerAndLogin("rag-off@example.com")
            setProfile(token, languageId = 1)
            reindexBody()

            val response =
                client.post("/api/v1/ai/chat") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"mode":"general","message":"環境を説明して"}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(0, payload["sources"]!!.jsonArray.size, "disabled RAG must ground nothing")
        }

    @Test
    fun `blob embeddings survive an h2 round trip with byte fidelity`() {
        val vector = floatArrayOf(0.25f, -0.5f, 0.125f, 3.75f)
        val bytes = VectorMath.toBytes(vector)
        val restored = VectorMath.fromBytes(bytes)
        assertTrue(vector.contentEquals(restored))
        assertEquals(0.0, VectorMath.cosine(vector, restored) - 1.0, 1e-12)
    }
}

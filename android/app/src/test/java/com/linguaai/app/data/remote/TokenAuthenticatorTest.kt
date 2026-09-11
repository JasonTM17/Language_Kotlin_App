package com.linguaai.app.data.remote

import com.linguaai.app.domain.repository.SessionStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The 401 -> refresh -> retry flow.
 *
 * This is the highest-risk code in the app: a mistake here either loops forever
 * on every request or silently signs the user out. It is exercised against a
 * real OkHttp client and a real MockWebServer rather than a hand-rolled fake, so
 * the authenticator sees genuine OkHttp response objects and priorResponse
 * chains.
 */
class TokenAuthenticatorTest {

    private lateinit var server: MockWebServer
    private lateinit var sessionStore: FakeSessionStore
    private lateinit var bareRetrofit: Retrofit

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sessionStore = FakeSessionStore(access = "stale-access", refresh = "refresh-1")
        bareRetrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Client with the Authorization header attached, as the real app does. */
    private fun authedClient(): OkHttpClient = OkHttpClient.Builder()
        .authenticator(TokenAuthenticator(sessionStore, bareRetrofit))
        .addInterceptor { chain ->
            val token = sessionStore.accessTokenSync()
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build(),
            )
        }
        .build()

    private fun protectedRequest() = Request.Builder()
        .url(server.url("/api/v1/lessons"))
        .build()

    private fun refreshResponse(access: String, refresh: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""{"tokens":{"accessToken":"$access","refreshToken":"$refresh","expiresIn":3600}}""")

    @Test
    fun `a 401 refreshes and the retry carries the new token`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(refreshResponse("fresh-access", "refresh-2"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        authedClient().newCall(protectedRequest()).execute().use { response ->
            assertEquals(200, response.code)
        }

        // First request carried the stale token.
        val first = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(first)
        assertEquals("Bearer stale-access", first!!.getHeader("Authorization"))

        // Second request was the refresh call.
        val refresh = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(refresh)
        assertEquals("/api/v1/auth/refresh", refresh!!.path)

        // Third was the retry, and it must carry the NEW token.
        val retry = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(retry)
        assertEquals("Bearer fresh-access", retry!!.getHeader("Authorization"))

        // Both tokens were persisted, so the rotated refresh token is not lost.
        assertEquals("fresh-access", sessionStore.accessTokenSync())
        assertEquals("refresh-2", sessionStore.refreshTokenSync())
    }

    @Test
    fun `a failed refresh clears the session and does not retry`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        authedClient().newCall(protectedRequest()).execute().use { response ->
            // The original 401 is surfaced rather than retried into a loop.
            assertEquals(401, response.code)
        }

        assertTrue(
            "session should be cleared after a failed refresh",
            awaitCondition { sessionStore.accessTokenSync() == null },
        )
        assertNull(sessionStore.refreshTokenSync())

        // Exactly two requests: the original and one refresh attempt, no retry.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a request with no authorization header is not refreshed`() {
        server.enqueue(MockResponse().setResponseCode(401))

        // No auth interceptor: the request goes out unauthenticated.
        val client = OkHttpClient.Builder()
            .authenticator(TokenAuthenticator(sessionStore, bareRetrofit))
            .build()

        client.newCall(protectedRequest()).execute().use { response ->
            assertEquals(401, response.code)
        }

        // Only the original request; no refresh was attempted.
        assertEquals(1, server.requestCount)
        // The session is untouched because the authenticator bailed out early.
        assertEquals("stale-access", sessionStore.accessTokenSync())
    }

    @Test
    fun `an auth endpoint 401 is never refreshed`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val client = OkHttpClient.Builder()
            .authenticator(TokenAuthenticator(sessionStore, bareRetrofit))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer stale-access")
                        .build(),
                )
            }
            .build()

        val request = Request.Builder().url(server.url("/api/v1/auth/login")).build()
        client.newCall(request).execute().use { response ->
            assertEquals(401, response.code)
        }

        // Refreshing on a failing auth call would recurse; only one request goes out.
        assertEquals(1, server.requestCount)
        assertEquals("stale-access", sessionStore.accessTokenSync())
    }

    /** The authenticator clears the session on a background scope, so poll briefly. */
    private fun awaitCondition(timeoutMs: Long = 2_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    /** In-memory [SessionStore]; the real one needs an Android Context. */
    private class FakeSessionStore(
        access: String? = null,
        refresh: String? = null,
    ) : SessionStore {

        private val accessState = MutableStateFlow(access)
        private val refreshState = MutableStateFlow(refresh)

        override fun accessTokenSync(): String? = accessState.value

        override fun refreshTokenSync(): String? = refreshState.value

        override val accessFlow: Flow<String?> = accessState
        override val refreshFlow: Flow<String?> = refreshState

        override suspend fun saveTokens(accessToken: String, refreshToken: String) {
            accessState.value = accessToken
            refreshState.value = refreshToken
        }

        override suspend fun clear() {
            accessState.value = null
            refreshState.value = null
        }
    }
}

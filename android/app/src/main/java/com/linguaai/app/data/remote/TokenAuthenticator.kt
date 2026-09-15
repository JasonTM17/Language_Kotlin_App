package com.linguaai.app.data.remote

import com.linguaai.app.data.remote.api.AuthApi
import com.linguaai.app.data.remote.dto.RefreshRequestDto
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] implementing the 401 -> refresh -> retry flow with
 * three guards against the classic failure modes:
 *
 *  1. Single-flight: concurrent 401s share one refresh call (mutex).
 *  2. Stale-retry detection: if the failing request already carries the
 *     *current* access token, refreshing will not help -> give up (logout)
 *     instead of looping forever.
 *  3. Refresh calls run on a bare Retrofit instance without this
 *     authenticator, so a failing refresh can never recurse.
 */
@Singleton
class TokenAuthenticator
    @Inject
    constructor(
        private val sessionManager: SessionStore,
        @Named("bareRetrofit") private val bareRetrofit: Retrofit,
    ) : Authenticator {
        private val refreshMutex = Mutex()

        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            // Never attempt token flow for auth endpoints themselves.
            if (response.request.header("Authorization") == null ||
                response.request.url.encodedPath
                    .contains("/auth/")
            ) {
                return null
            }
            if (responseCount(response) >= 2) return null

            val staleToken = response.request.header("Authorization")?.removePrefix("Bearer ")

            val refreshed =
                runBlocking {
                    refreshMutex.withLock {
                        val current = sessionManager.accessTokenSync()
                        when {
                            // Another thread already refreshed while we waited.
                            current != null && current != staleToken -> true
                            else -> refreshAccessToken()
                        }
                    }
                }

            val newToken = sessionManager.accessTokenSync()
            if (!refreshed || newToken == null) {
                // Refresh failed: clear synchronously before any offline
                // content fallback can read the previous language scope.
                runBlocking { sessionManager.clear() }
                return null
            }

            return response.request
                .newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        }

        private fun refreshAccessToken(): Boolean {
            val refreshToken = sessionManager.refreshTokenSync() ?: return false
            val api = bareRetrofit.create(AuthApi::class.java)
            return runBlocking {
                when (val result = safeApiCall { api.refresh(RefreshRequestDto(refreshToken)) }) {
                    is AppResult.Success -> {
                        sessionManager.saveTokens(
                            accessToken = result.data.tokens.accessToken,
                            refreshToken = result.data.tokens.refreshToken,
                        )
                        true
                    }
                    is AppResult.Failure -> false
                }
            }
        }

        private fun responseCount(response: Response): Int {
            var count = 1
            var prior = response.priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }
    }

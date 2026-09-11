package com.linguaai.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Session token storage, expressed as an abstraction so the OkHttp auth path can
 * be tested without an Android [android.content.Context].
 *
 * `TokenAuthenticator` is the highest-risk code in the app — a mistake there
 * either loops forever or silently signs the user out — so it must be testable
 * on the JVM. The synchronous accessors exist because OkHttp interceptors run on
 * their own threads and cannot suspend.
 */
interface SessionStore {

    /** Current access token, or null when signed out. Non-suspending by design. */
    fun accessTokenSync(): String?

    /** Current refresh token, or null when signed out. Non-suspending by design. */
    fun refreshTokenSync(): String?

    val accessFlow: Flow<String?>

    val refreshFlow: Flow<String?>

    suspend fun saveTokens(accessToken: String, refreshToken: String)

    suspend fun clear()
}

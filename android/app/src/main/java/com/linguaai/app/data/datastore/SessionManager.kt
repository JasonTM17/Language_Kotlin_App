package com.linguaai.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Holds the JWT session. DataStore is the durable source of truth; an
 * in-memory cache serves the synchronous OkHttp interceptor/authenticator
 * path without blocking on disk for every request.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : com.linguaai.app.domain.repository.SessionStore {

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedRefreshToken: String? = null

    @Volatile
    private var cacheInitialized = false

    override val accessFlow: Flow<String?> = context.tokenDataStore.data.map { it[ACCESS_TOKEN] }
    override val refreshFlow: Flow<String?> = context.tokenDataStore.data.map { it[REFRESH_TOKEN] }

    /** Synchronous access for OkHttp's interceptor/authenticator threads. */
    override fun accessTokenSync(): String? {
        ensureCache()
        return cachedAccessToken
    }

    override fun refreshTokenSync(): String? {
        ensureCache()
        return cachedRefreshToken
    }

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        cachedAccessToken = accessToken
        cachedRefreshToken = refreshToken
        context.tokenDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            prefs[REFRESH_TOKEN] = refreshToken
        }
    }

    override suspend fun clear() {
        cachedAccessToken = null
        cachedRefreshToken = null
        context.tokenDataStore.edit { it.clear() }
    }

    private fun ensureCache() {
        if (!cacheInitialized) {
            synchronized(this) {
                if (!cacheInitialized) {
                    // One-time small read on first network call of the process.
                    val prefs = runBlocking { context.tokenDataStore.data.first() }
                    cachedAccessToken = prefs[ACCESS_TOKEN]
                    cachedRefreshToken = prefs[REFRESH_TOKEN]
                    cacheInitialized = true
                }
            }
        }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = longPreferencesKey("user_id")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }
}

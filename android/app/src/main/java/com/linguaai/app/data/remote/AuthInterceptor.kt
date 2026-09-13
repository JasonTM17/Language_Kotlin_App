package com.linguaai.app.data.remote

import com.linguaai.app.data.datastore.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Adds the bearer access token to every outgoing request when present. */
@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val sessionManager: SessionManager,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val token = sessionManager.accessTokenSync()
            val authenticated =
                if (token != null) {
                    request.newBuilder().header("Authorization", "Bearer $token").build()
                } else {
                    request
                }
            return chain.proceed(authenticated)
        }
    }

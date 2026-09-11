package com.linguaai.app.di

import com.linguaai.app.BuildConfig
import com.linguaai.app.data.remote.AuthInterceptor
import com.linguaai.app.data.remote.TokenAuthenticator
import com.linguaai.app.data.remote.api.AuthApi
import com.linguaai.app.data.remote.api.ContentApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    /**
     * Exposes the single configured [Json] instance so non-network components
     * (local cache codecs) encode and decode with exactly the same settings as
     * the wire format.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = json

    @Provides
    @Singleton
    @Named("logging")
    fun loggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        // Body logging is disabled so tokens and credentials never reach logs.
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
    }

    @Provides
    @Singleton
    @Named("bare")
    fun bareOkHttpClient(@Named("logging") logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

    @Provides
    @Singleton
    @Named("bareRetrofit")
    fun bareRetrofit(@Named("bare") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun okHttpClient(
        @Named("logging") logging: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(authenticator)
            .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun contentApi(retrofit: Retrofit): ContentApi = retrofit.create(ContentApi::class.java)

    @Provides
    @Singleton
    fun aiApi(retrofit: Retrofit): com.linguaai.app.data.remote.api.AiApi = retrofit.create(com.linguaai.app.data.remote.api.AiApi::class.java)

    @Provides
    @Singleton
    fun progressApi(retrofit: Retrofit): com.linguaai.app.data.remote.api.ProgressApi = retrofit.create(com.linguaai.app.data.remote.api.ProgressApi::class.java)
}

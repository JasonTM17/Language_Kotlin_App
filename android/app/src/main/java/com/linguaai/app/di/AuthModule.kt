package com.linguaai.app.di

import com.linguaai.app.data.datastore.SessionManager
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.repository.AuthRepository
import com.linguaai.app.domain.repository.SessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Authentication bindings: the remote-backed repository serves the domain. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: RemoteAuthRepository): AuthRepository

    /**
     * The OkHttp auth path depends on [SessionStore] rather than the concrete
     * DataStore-backed implementation, which is what makes `TokenAuthenticator`
     * testable without an Android Context.
     */
    @Binds
    @Singleton
    abstract fun bindSessionStore(impl: SessionManager): SessionStore
}

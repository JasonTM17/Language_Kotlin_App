package com.linguaai.app.di

import com.linguaai.app.data.repository.InMemoryAuthRepository
import com.linguaai.app.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Authentication bindings. The in-memory implementation is temporary and is
 * swapped for the remote-backed repository when networking lands.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: InMemoryAuthRepository): AuthRepository
}

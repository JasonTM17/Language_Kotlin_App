package com.linguaai.app.di

import com.linguaai.app.domain.srs.ReviewScheduler
import com.linguaai.app.domain.srs.Sm2LiteScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SrsModule {

    @Binds
    @Singleton
    abstract fun bindReviewScheduler(impl: Sm2LiteScheduler): ReviewScheduler
}

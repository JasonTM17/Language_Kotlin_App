package com.linguaai.app.di

import com.linguaai.app.data.repository.LearningContentRepositoryImpl
import com.linguaai.app.domain.repository.LearningContentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ContentModule {
    @Binds
    @Singleton
    abstract fun bindLearningContentRepository(impl: LearningContentRepositoryImpl): LearningContentRepository
}

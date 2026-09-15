package com.linguaai.app.di

import android.content.Context
import androidx.room.Room
import com.linguaai.app.data.local.LinguaDatabase
import com.linguaai.app.data.local.dao.GrammarDao
import com.linguaai.app.data.local.dao.LessonDao
import com.linguaai.app.data.local.dao.SyncDao
import com.linguaai.app.data.local.dao.VocabularyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): LinguaDatabase =
        Room
            .databaseBuilder(context, LinguaDatabase::class.java, LinguaDatabase.NAME)
            .addMigrations(
                LinguaDatabase.MIGRATION_1_2,
                LinguaDatabase.MIGRATION_2_3,
                LinguaDatabase.MIGRATION_3_4,
                LinguaDatabase.MIGRATION_4_5,
                LinguaDatabase.MIGRATION_5_6,
            ).fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun lessonDao(database: LinguaDatabase): LessonDao = database.lessonDao()

    @Provides
    fun vocabularyDao(database: LinguaDatabase): VocabularyDao = database.vocabularyDao()

    @Provides
    fun grammarDao(database: LinguaDatabase): GrammarDao = database.grammarDao()

    @Provides
    fun syncDao(database: LinguaDatabase): SyncDao = database.syncDao()

    @Provides
    fun aiMessageCacheDao(database: LinguaDatabase): com.linguaai.app.data.local.dao.AiMessageCacheDao = database.aiMessageCacheDao()

    @Provides
    fun progressCacheDao(database: LinguaDatabase): com.linguaai.app.data.local.dao.ProgressCacheDao = database.progressCacheDao()
}

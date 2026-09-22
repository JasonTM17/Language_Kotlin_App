package com.linguaai.app.domain.repository

import com.linguaai.app.data.remote.dto.GrammarDto
import com.linguaai.app.data.remote.dto.LanguageDto
import com.linguaai.app.data.remote.dto.LessonDto
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.remote.dto.QuizDto
import com.linguaai.app.data.remote.dto.QuizResultDto
import com.linguaai.app.data.remote.dto.QuizSubmissionDto
import com.linguaai.app.domain.model.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first content access: reads come from the local cache as a Flow,
 * refreshes pull from the network and repopulate the cache.
 */
interface LearningContentRepository {
    suspend fun languages(): AppResult<List<LanguageDto>>

    suspend fun refreshLessons(
        languageId: Long?,
        level: String?,
    ): AppResult<Unit>

    fun observeLessons(
        languageId: Long?,
        level: String?,
    ): Flow<List<LessonSummaryDto>>

    suspend fun refreshLesson(id: Long): AppResult<LessonDto>

    suspend fun refreshVocabulary(
        languageId: Long?,
        level: String?,
        category: String?,
        query: String?,
    ): AppResult<Unit>

    fun observeVocabulary(
        languageId: Long?,
        level: String?,
        category: String?,
        query: String?,
    ): Flow<List<com.linguaai.app.domain.model.VocabularyCard>>

    fun observeFavorites(languageId: Long): Flow<List<com.linguaai.app.domain.model.VocabularyCard>>

    suspend fun toggleFavorite(id: Long)

    suspend fun refreshGrammar(
        languageId: Long?,
        level: String?,
    ): AppResult<Unit>

    fun observeGrammar(
        languageId: Long?,
        level: String?,
    ): Flow<List<GrammarDto>>

    suspend fun grammarById(id: Long): AppResult<GrammarDto>

    suspend fun quiz(id: Long): AppResult<QuizDto>

    suspend fun submitQuiz(
        id: Long,
        submission: QuizSubmissionDto,
    ): AppResult<QuizResultDto>
}

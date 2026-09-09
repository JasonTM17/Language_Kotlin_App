package com.linguaai.app.data.repository

import com.linguaai.app.data.remote.api.ContentApi
import com.linguaai.app.data.remote.dto.GrammarDto
import com.linguaai.app.data.remote.dto.LanguageDto
import com.linguaai.app.data.remote.dto.LessonDto
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.remote.dto.QuizDto
import com.linguaai.app.data.remote.dto.QuizResultDto
import com.linguaai.app.data.remote.dto.QuizSubmissionDto
import com.linguaai.app.data.remote.dto.VocabularyDto
import com.linguaai.app.data.remote.safeApiCall
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.LearningContentRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Network-backed implementation; the offline cache layer wraps this later. */
@Singleton
class LearningContentRepositoryImpl @Inject constructor(
    private val contentApi: ContentApi,
) : LearningContentRepository {

    override suspend fun languages() = safeApiCall { contentApi.languages() }

    override suspend fun lessons(
        languageId: Long?,
        level: String?,
        type: String?,
    ): AppResult<List<LessonSummaryDto>> = safeApiCall { contentApi.lessons(languageId, level, type) }

    override suspend fun lesson(id: Long): AppResult<LessonDto> = safeApiCall { contentApi.lesson(id) }

    override suspend fun vocabulary(
        languageId: Long?,
        level: String?,
        category: String?,
        query: String?,
    ): AppResult<List<VocabularyDto>> = safeApiCall { contentApi.vocabulary(languageId, level, category, query) }

    override suspend fun grammar(languageId: Long?, level: String?): AppResult<List<GrammarDto>> =
        safeApiCall { contentApi.grammar(languageId, level) }

    override suspend fun grammarById(id: Long): AppResult<GrammarDto> = safeApiCall { contentApi.grammarById(id) }

    override suspend fun quiz(id: Long): AppResult<QuizDto> = safeApiCall { contentApi.quiz(id) }

    override suspend fun submitQuiz(id: Long, submission: QuizSubmissionDto): AppResult<QuizResultDto> =
        safeApiCall { contentApi.submitQuiz(id, submission) }
}

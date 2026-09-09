package com.linguaai.app.domain.repository

import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.data.remote.dto.GrammarDto
import com.linguaai.app.data.remote.dto.LanguageDto
import com.linguaai.app.data.remote.dto.LessonDto
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.remote.dto.QuizDto
import com.linguaai.app.data.remote.dto.QuizResultDto
import com.linguaai.app.data.remote.dto.QuizSubmissionDto
import com.linguaai.app.data.remote.dto.VocabularyDto

/** Contract for learning content access; implemented by the data layer. */
interface LearningContentRepository {
    suspend fun languages(): AppResult<List<LanguageDto>>
    suspend fun lessons(languageId: Long?, level: String?, type: String?): AppResult<List<LessonSummaryDto>>
    suspend fun lesson(id: Long): AppResult<LessonDto>
    suspend fun vocabulary(
        languageId: Long?,
        level: String? = null,
        category: String? = null,
        query: String? = null,
    ): AppResult<List<VocabularyDto>>
    suspend fun grammar(languageId: Long?, level: String?): AppResult<List<GrammarDto>>
    suspend fun grammarById(id: Long): AppResult<GrammarDto>
    suspend fun quiz(id: Long): AppResult<QuizDto>
    suspend fun submitQuiz(id: Long, submission: QuizSubmissionDto): AppResult<QuizResultDto>
}

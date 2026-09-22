package com.linguaai.app.data.repository

import com.linguaai.app.data.local.dao.GrammarDao
import com.linguaai.app.data.local.dao.LessonDao
import com.linguaai.app.data.local.dao.VocabularyDao
import com.linguaai.app.data.local.entity.VocabularyEntity
import com.linguaai.app.data.remote.api.ContentApi
import com.linguaai.app.data.remote.dto.GrammarDto
import com.linguaai.app.data.remote.dto.LanguageDto
import com.linguaai.app.data.remote.dto.LessonDto
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.remote.dto.ProgressEventTypes
import com.linguaai.app.data.remote.dto.QuizDto
import com.linguaai.app.data.remote.dto.QuizResultDto
import com.linguaai.app.data.remote.dto.QuizSubmissionDto
import com.linguaai.app.data.remote.dto.VocabularyProgressSnapshotDto
import com.linguaai.app.data.remote.safeApiCall
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.LearningContentRepository
import com.linguaai.app.domain.repository.ProgressRepository
import com.linguaai.app.work.ProgressEventRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first content store: network refreshes repopulate Room, and UI
 * observes Room so cached content stays available without connectivity.
 */
@Singleton
class LearningContentRepositoryImpl
    @Inject
    constructor(
        private val contentApi: ContentApi,
        private val lessonDao: LessonDao,
        private val vocabularyDao: VocabularyDao,
        private val grammarDao: GrammarDao,
        private val progressRepository: ProgressRepository,
        private val progressEventRecorder: ProgressEventRecorder,
    ) : LearningContentRepository {
        override suspend fun languages(): AppResult<List<LanguageDto>> = safeApiCall { contentApi.languages() }

        // ---- lessons ----

        override suspend fun refreshLessons(
            languageId: Long?,
            level: String?,
        ): AppResult<Unit> {
            val result = safeApiCall { contentApi.lessons(languageId, level, null) }
            return when (result) {
                is AppResult.Success -> {
                    lessonDao.upsertAll(result.data.map { it.toEntity() })
                    AppResult.Success(Unit)
                }
                is AppResult.Failure -> result
            }
        }

        override fun observeLessons(
            languageId: Long?,
            level: String?,
        ): Flow<List<LessonSummaryDto>> = lessonDao.observeLessons(languageId, level).map { list -> list.map { it.toSummaryDto() } }

        override suspend fun refreshLesson(id: Long): AppResult<LessonDto> {
            val result = safeApiCall { contentApi.lesson(id) }
            return when (result) {
                is AppResult.Success -> {
                    lessonDao.upsertAll(listOf(result.data.toEntity()))
                    AppResult.Success(result.data)
                }
                is AppResult.Failure -> result
            }
        }

        // ---- vocabulary ----

        override suspend fun refreshVocabulary(
            languageId: Long?,
            level: String?,
            category: String?,
            query: String?,
        ): AppResult<Unit> {
            val result = safeApiCall { contentApi.vocabulary(languageId, level, category, query) }
            return when (result) {
                is AppResult.Success -> {
                    // Preserve local review state: favorites/mastery live in this table.
                    val preserved =
                        result.data
                            .mapNotNull { dto -> vocabularyDao.findById(dto.id)?.let { dto.id to it } }
                            .toMap()
                    val entities =
                        result.data.map { dto ->
                            val old = preserved[dto.id]
                            if (old != null) {
                                dto.toEntity().copy(
                                    favorite = old.favorite,
                                    masteryLevel = old.masteryLevel,
                                    reviewCount = old.reviewCount,
                                    correctCount = old.correctCount,
                                    wrongCount = old.wrongCount,
                                    lastReviewedAt = old.lastReviewedAt,
                                    nextReviewAt = old.nextReviewAt,
                                    stateUpdatedAt = old.stateUpdatedAt,
                                )
                            } else {
                                dto.toEntity()
                            }
                        }
                    vocabularyDao.upsertAll(entities)
                    // Content rows must exist before server-owned per-word state
                    // can hydrate them; pending local outbox rows stay protected
                    // by VocabularyDao.applyProgressIfNoPending.
                    progressRepository.syncVocabularyProgress()
                    AppResult.Success(Unit)
                }
                is AppResult.Failure -> result
            }
        }

        override fun observeVocabulary(
            languageId: Long?,
            level: String?,
            category: String?,
            query: String?,
        ): Flow<List<com.linguaai.app.domain.model.VocabularyCard>> =
            vocabularyDao.observeVocabulary(languageId, level, category, query).map { list ->
                list.map { entity ->
                    entity.toDto().let { dto ->
                        com.linguaai.app.domain.model.VocabularyCard(
                            id = entity.id,
                            languageId = entity.languageId,
                            level = entity.level,
                            word = entity.word,
                            reading = entity.reading,
                            pronunciation = entity.pronunciation,
                            meaning = entity.meaning,
                            example = entity.example,
                            exampleTranslation = entity.exampleTranslation,
                            category = entity.category,
                            favorite = entity.favorite,
                            masteryLevel = entity.masteryLevel,
                        )
                    }
                }
            }

        override fun observeFavorites(languageId: Long): Flow<List<com.linguaai.app.domain.model.VocabularyCard>> =
            vocabularyDao.observeFavorites(languageId).map { list ->
                list.map { entity ->
                    entity.toDto().let { dto ->
                        com.linguaai.app.domain.model.VocabularyCard(
                            id = entity.id,
                            languageId = entity.languageId,
                            level = entity.level,
                            word = entity.word,
                            reading = entity.reading,
                            pronunciation = entity.pronunciation,
                            meaning = entity.meaning,
                            example = entity.example,
                            exampleTranslation = entity.exampleTranslation,
                            category = entity.category,
                            favorite = entity.favorite,
                            masteryLevel = entity.masteryLevel,
                        )
                    }
                }
            }

        override suspend fun toggleFavorite(id: Long) {
            val current = vocabularyDao.findById(id) ?: return
            val updated =
                current.copy(
                    favorite = !current.favorite,
                    stateUpdatedAt = nextStateVersion(current.stateUpdatedAt),
                )
            progressEventRecorder.record(
                eventType = ProgressEventTypes.VOCABULARY_STATE_SYNC,
                refId = id,
                vocabularyProgress = updated.toProgressSnapshot(),
                localUpdate = { vocabularyDao.upsert(updated) },
            )
        }

        // ---- grammar ----

        override suspend fun refreshGrammar(
            languageId: Long?,
            level: String?,
        ): AppResult<Unit> {
            val result = safeApiCall { contentApi.grammar(languageId, level) }
            return when (result) {
                is AppResult.Success -> {
                    grammarDao.upsertAll(result.data.map { it.toEntity() })
                    AppResult.Success(Unit)
                }
                is AppResult.Failure -> result
            }
        }

        override fun observeGrammar(
            languageId: Long?,
            level: String?,
        ): Flow<List<GrammarDto>> = grammarDao.observeGrammar(languageId, level).map { list -> list.map { it.toDto() } }

        override suspend fun grammarById(id: Long): AppResult<GrammarDto> {
            val cached = grammarDao.findById(id)
            val result = safeApiCall { contentApi.grammarById(id) }
            return when (result) {
                is AppResult.Success -> {
                    grammarDao.upsertAll(listOf(result.data.toEntity()))
                    AppResult.Success(result.data)
                }
                is AppResult.Failure -> cached?.let { AppResult.Success(it.toDto()) } ?: result
            }
        }

        // ---- quizzes (always fresh; small payloads) ----

        override suspend fun quiz(id: Long): AppResult<QuizDto> = safeApiCall { contentApi.quiz(id) }

        override suspend fun submitQuiz(
            id: Long,
            submission: QuizSubmissionDto,
        ): AppResult<QuizResultDto> = safeApiCall { contentApi.submitQuiz(id, submission) }
    }

private fun VocabularyEntity.toProgressSnapshot(): VocabularyProgressSnapshotDto =
    VocabularyProgressSnapshotDto(
        favorite = favorite,
        masteryLevel = masteryLevel,
        reviewCount = reviewCount,
        correctCount = correctCount,
        wrongCount = wrongCount,
        lastReviewedAtEpochMillis = lastReviewedAt,
        nextReviewAtEpochMillis = nextReviewAt,
        stateUpdatedAtEpochMillis = stateUpdatedAt,
    )

private fun nextStateVersion(current: Long?): Long = maxOf(current ?: 0L, System.currentTimeMillis()) + 1L

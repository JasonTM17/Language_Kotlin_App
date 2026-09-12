package com.linguaai.server.repository

import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.api.dto.AnswerFeedbackDto
import com.linguaai.server.api.dto.GrammarDto
import com.linguaai.server.api.dto.GrammarExampleDto
import com.linguaai.server.api.dto.LanguageDto
import com.linguaai.server.api.dto.LessonDto
import com.linguaai.server.api.dto.LessonSummaryDto
import com.linguaai.server.api.dto.QuizDto
import com.linguaai.server.api.dto.QuizQuestionDto
import com.linguaai.server.api.dto.QuizResultDto
import com.linguaai.server.api.dto.QuizSubmissionDto
import com.linguaai.server.api.dto.VocabularyDto
import com.linguaai.server.db.GrammarLessons
import com.linguaai.server.db.Languages
import com.linguaai.server.db.Lessons
import com.linguaai.server.db.QuizAttempts
import com.linguaai.server.db.QuizAnswers
import com.linguaai.server.db.QuizQuestions
import com.linguaai.server.db.Quizzes
import com.linguaai.server.db.UserMistakes
import com.linguaai.server.db.Vocabularies
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

/** Characters of a question prompt kept for list previews. */
private const val PROMPT_PREVIEW_LENGTH = 80

/**
 * Read-side content store (languages, lessons, vocabulary, grammar, quizzes)
 * plus server-side quiz grading. Every function opens its own transaction.
 */
class ContentRepository {

    private val json = Json { ignoreUnknownKeys = true }

    fun findLanguages(): List<LanguageDto> = transaction {
        Languages.selectAll().orderBy(Languages.id).map { row ->
            LanguageDto(
                id = row[Languages.id],
                code = row[Languages.code],
                name = row[Languages.name],
                levels = row[Languages.levels].split(",").map { it.trim() },
            )
        }
    }

    /**
     * Single-language lookup.
     *
     * Exists so prompt construction can name the language the learner is actually
     * studying. Passing a numeric id to a model is meaningless — "a learner of
     * language #1" tells it nothing about whether to use Japanese or Spanish.
     */
    fun findLanguageById(id: Long): LanguageDto? = transaction {
        Languages.selectAll()
            .andWhere { Languages.id eq id }
            .firstOrNull()
            ?.let { row ->
                LanguageDto(
                    id = row[Languages.id],
                    code = row[Languages.code],
                    name = row[Languages.name],
                    levels = row[Languages.levels].split(",").map { it.trim() },
                )
            }
    }

    fun findLessons(languageId: Long?, level: String?, type: String?): List<LessonSummaryDto> = transaction {
        val stmt = Lessons.selectAll()
        languageId?.let { stmt.andWhere { Lessons.languageId eq it } }
        level?.let { stmt.andWhere { Lessons.level eq it } }
        type?.let { stmt.andWhere { Lessons.type eq it } }
        stmt.orderBy(Lessons.id).map(::toLessonSummary)
    }

    fun findLessonById(id: Long): LessonDto = transaction {
        Lessons.selectAll().andWhere { Lessons.id eq id }
            .firstOrNull()?.let(::toLesson)
            ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "Lesson not found")
    }

    fun findVocabulary(
        languageId: Long?,
        level: String?,
        category: String?,
        query: String?,
    ): List<VocabularyDto> = transaction {
        val stmt = Vocabularies.selectAll()
        languageId?.let { stmt.andWhere { Vocabularies.languageId eq it } }
        level?.let { stmt.andWhere { Vocabularies.level eq it } }
        category?.let { stmt.andWhere { Vocabularies.category eq it } }
        query?.takeIf { it.isNotBlank() }?.let { term ->
            val pattern = "%${term.trim()}%"
            stmt.andWhere {
                (Vocabularies.word like pattern) or
                    (Vocabularies.reading like pattern) or
                    (Vocabularies.meaning like pattern)
            }
        }
        stmt.orderBy(Vocabularies.id).map(::toVocabulary)
    }

    fun findVocabularyById(id: Long): VocabularyDto = transaction {
        Vocabularies.selectAll().andWhere { Vocabularies.id eq id }
            .firstOrNull()?.let(::toVocabulary)
            ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "Vocabulary not found")
    }

    fun findGrammar(languageId: Long?, level: String?): List<GrammarDto> = transaction {
        val stmt = GrammarLessons.selectAll()
        languageId?.let { stmt.andWhere { GrammarLessons.languageId eq it } }
        level?.let { stmt.andWhere { GrammarLessons.level eq it } }
        stmt.orderBy(GrammarLessons.id).map(::toGrammar)
    }

    fun findGrammarById(id: Long): GrammarDto = transaction {
        GrammarLessons.selectAll().andWhere { GrammarLessons.id eq id }
            .firstOrNull()?.let(::toGrammar)
            ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "Grammar lesson not found")
    }

    fun findQuizById(id: Long): QuizDto = transaction {
        val quiz = Quizzes.selectAll().andWhere { Quizzes.id eq id }
            .firstOrNull()
            ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "Quiz not found")
        val questions = QuizQuestions.selectAll().andWhere { QuizQuestions.quizId eq id }
            .orderBy(QuizQuestions.position, SortOrder.ASC)
            .map { row ->
                QuizQuestionDto(
                    id = row[QuizQuestions.id],
                    questionType = row[QuizQuestions.questionType],
                    prompt = row[QuizQuestions.prompt],
                    options = row[QuizQuestions.options]?.let { json.decodeFromString<List<String>>(it) }
                        ?: emptyList(),
                    position = row[QuizQuestions.position],
                )
            }
        QuizDto(
            id = quiz[Quizzes.id],
            languageId = quiz[Quizzes.languageId],
            level = quiz[Quizzes.level],
            title = quiz[Quizzes.title],
            description = quiz[Quizzes.description],
            questions = questions,
        )
    }

    /**
     * Grades a submission server-side (correct answers never reach the client
     * before submission), persists the attempt, and records weak topics as
     * mistakes for the AI tutor context.
     */
    fun submitQuiz(userId: Long, quizId: Long, submission: QuizSubmissionDto): QuizResultDto = transaction {
        val quiz = Quizzes.selectAll().andWhere { Quizzes.id eq quizId }.firstOrNull()
            ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "Quiz not found")

        val questionRows = QuizQuestions.selectAll().andWhere { QuizQuestions.quizId eq quizId }
            .orderBy(QuizQuestions.position, SortOrder.ASC).toList()
        val submissionByQuestion = submission.answers.associateBy { it.questionId }

        val feedback = questionRows.map { row ->
            val submitted = submissionByQuestion[row[QuizQuestions.id]]?.answer
            val correct = submitted != null &&
                submitted.trim().equals(row[QuizQuestions.correctAnswer].trim(), ignoreCase = true)
            AnswerFeedbackDto(
                questionId = row[QuizQuestions.id],
                correct = correct,
                correctAnswer = row[QuizQuestions.correctAnswer],
                explanation = row[QuizQuestions.explanation],
            )
        }

        val score = feedback.count { it.correct }
        val now = LocalDateTime.now()
        val attemptId = QuizAttempts.insert { insertRow ->
            insertRow[QuizAttempts.userId] = userId
            insertRow[QuizAttempts.quizId] = quizId
            insertRow[QuizAttempts.score] = score
            insertRow[QuizAttempts.total] = questionRows.size
            insertRow[QuizAttempts.durationSeconds] = submission.durationSeconds
            insertRow[QuizAttempts.completedAt] = now
        } get QuizAttempts.id

        feedback.forEach { item ->
            QuizAnswers.insert { insertRow ->
                insertRow[QuizAnswers.attemptId] = attemptId
                insertRow[QuizAnswers.questionId] = item.questionId
                insertRow[QuizAnswers.answer] = submissionByQuestion[item.questionId]?.answer
                insertRow[QuizAnswers.correct] = item.correct
            }
        }

        val weakTopics = feedback.filter { !it.correct }
            .mapNotNull { item ->
                questionRows.firstOrNull { it[QuizQuestions.id] == item.questionId }
                    ?.let { row -> row[QuizQuestions.prompt].take(PROMPT_PREVIEW_LENGTH) }
            }
        weakTopics.forEach { topic ->
            UserMistakes.insert { insertRow ->
                insertRow[UserMistakes.userId] = userId
                insertRow[UserMistakes.languageId] = quiz[Quizzes.languageId]
                insertRow[UserMistakes.topic] = topic
                insertRow[UserMistakes.detail] = "Wrong answer in quiz: ${quiz[Quizzes.title]}"
                insertRow[UserMistakes.sourceType] = "QUIZ"
                insertRow[UserMistakes.createdAt] = now
            }
        }

        QuizResultDto(
            attemptId = attemptId,
            quizId = quizId,
            score = score,
            total = questionRows.size,
            answers = feedback,
            weakTopics = weakTopics,
        )
    }

    // ---- row mappers ----

    private fun toLessonSummary(row: org.jetbrains.exposed.sql.ResultRow) = LessonSummaryDto(
        id = row[Lessons.id],
        languageId = row[Lessons.languageId],
        level = row[Lessons.level],
        title = row[Lessons.title],
        description = row[Lessons.description],
        type = row[Lessons.type],
        estimatedMinutes = row[Lessons.estimatedMinutes],
        difficulty = row[Lessons.difficulty],
    )

    private fun toLesson(row: org.jetbrains.exposed.sql.ResultRow) = LessonDto(
        id = row[Lessons.id],
        languageId = row[Lessons.languageId],
        level = row[Lessons.level],
        title = row[Lessons.title],
        description = row[Lessons.description],
        type = row[Lessons.type],
        estimatedMinutes = row[Lessons.estimatedMinutes],
        difficulty = row[Lessons.difficulty],
        content = row[Lessons.content],
    )

    private fun toVocabulary(row: org.jetbrains.exposed.sql.ResultRow) = VocabularyDto(
        id = row[Vocabularies.id],
        languageId = row[Vocabularies.languageId],
        level = row[Vocabularies.level],
        word = row[Vocabularies.word],
        reading = row[Vocabularies.reading],
        pronunciation = row[Vocabularies.pronunciation],
        meaning = row[Vocabularies.meaning],
        example = row[Vocabularies.example],
        exampleTranslation = row[Vocabularies.exampleTranslation],
        category = row[Vocabularies.category],
    )

    private fun toGrammar(row: org.jetbrains.exposed.sql.ResultRow): GrammarDto {
        val examples = row[GrammarLessons.examples]
            ?.split("\n")
            ?.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size == 2) GrammarExampleDto(parts[0].trim(), parts[1].trim()) else null
            }
            ?: emptyList()
        return GrammarDto(
            id = row[GrammarLessons.id],
            languageId = row[GrammarLessons.languageId],
            level = row[GrammarLessons.level],
            title = row[GrammarLessons.title],
            structure = row[GrammarLessons.structure],
            meaning = row[GrammarLessons.meaning],
            usage = row[GrammarLessons.usageNotes],
            examples = examples,
            notes = row[GrammarLessons.notes],
            difficulty = row[GrammarLessons.difficulty],
        )
    }
}

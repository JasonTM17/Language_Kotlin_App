package com.linguaai.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LanguageDto(
    val id: Long,
    val code: String,
    val name: String,
    val levels: List<String>,
)

@Serializable
data class LessonSummaryDto(
    val id: Long,
    val languageId: Long,
    val level: String,
    val title: String,
    val description: String? = null,
    val type: String,
    val estimatedMinutes: Int,
    val difficulty: Int,
)

@Serializable
data class LessonDto(
    val id: Long,
    val languageId: Long,
    val level: String,
    val title: String,
    val description: String? = null,
    val type: String,
    val estimatedMinutes: Int,
    val difficulty: Int,
    val content: String? = null,
)

@Serializable
data class VocabularyDto(
    val id: Long,
    val languageId: Long,
    val level: String,
    val word: String,
    val reading: String? = null,
    val pronunciation: String? = null,
    val meaning: String,
    val example: String? = null,
    val exampleTranslation: String? = null,
    val category: String? = null,
)

@Serializable
data class GrammarExampleDto(
    val sentence: String,
    val translation: String,
)

@Serializable
data class GrammarDto(
    val id: Long,
    val languageId: Long,
    val level: String,
    val title: String,
    val structure: String? = null,
    val meaning: String? = null,
    val usage: String? = null,
    val examples: List<GrammarExampleDto> = emptyList(),
    val notes: String? = null,
    val difficulty: Int,
)

@Serializable
data class QuizQuestionDto(
    val id: Long,
    val questionType: String,
    val prompt: String,
    val options: List<String> = emptyList(),
    val position: Int,
)

@Serializable
data class QuizDto(
    val id: Long,
    val languageId: Long,
    val level: String,
    val title: String,
    val description: String? = null,
    val questions: List<QuizQuestionDto> = emptyList(),
)

@Serializable
data class QuizSubmissionDto(
    val answers: List<QuizSubmissionAnswerDto>,
    val durationSeconds: Int? = null,
)

@Serializable
data class QuizSubmissionAnswerDto(
    val questionId: Long,
    val answer: String? = null,
)

@Serializable
data class AnswerFeedbackDto(
    val questionId: Long,
    val correct: Boolean,
    val correctAnswer: String,
    val explanation: String? = null,
)

@Serializable
data class QuizResultDto(
    val attemptId: Long,
    val quizId: Long,
    val score: Int,
    val total: Int,
    val answers: List<AnswerFeedbackDto>,
    val weakTopics: List<String> = emptyList(),
)

/** Server-side error envelope: {"error":{"code","message","requestId"}} */
@Serializable
data class ApiErrorEnvelopeDto(
    val error: ApiErrorBodyDto,
)

@Serializable
data class ApiErrorBodyDto(
    val code: String,
    val message: String,
    val requestId: String? = null,
)

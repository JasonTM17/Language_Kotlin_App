package com.linguaai.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AiChatRequestDto(
    val conversationId: Long? = null,
    val mode: String = "general",
    val message: String,
    val contextLessonId: Long? = null,
    val contextGrammarId: Long? = null,
)

@Serializable
data class AiChatResponseDto(
    val conversationId: Long,
    val reply: String,
    val mode: String,
    /**
     * Corpus chunks the tutor grounded this reply in. Defaulted so responses
     * from servers without RAG (or cached payloads) keep decoding.
     */
    val sources: List<AiSourceDto> = emptyList(),
)

/** One retrieved course-corpus chunk cited by the tutor. */
@Serializable
data class AiSourceDto(
    val title: String,
    val sourceType: String,
    val sourceId: Long,
    val chunkIndex: Int,
    val level: String? = null,
    val score: Double,
)

@Serializable
data class AiConversationDto(
    val id: Long,
    val title: String,
    val mode: String,
)

@Serializable
data class AiMessageDto(
    val id: Long,
    val role: String,
    val content: String,
)

@Serializable
data class ExplainRequestDto(
    val grammarId: Long? = null,
    val text: String? = null,
)

@Serializable
data class CorrectRequestDto(
    val sentence: String,
    val conversationId: Long? = null,
)

@Serializable
data class GenerateQuizRequestDto(
    val languageId: Long? = null,
    val level: String? = null,
    val topic: String? = null,
    val count: Int = 5,
)

@Serializable
data class GeneratedQuizQuestionDto(
    val prompt: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String? = null,
)

@Serializable
data class GeneratedQuizDto(
    val questions: List<GeneratedQuizQuestionDto>,
)

@Serializable
data class PracticeStartRequestDto(
    val scenario: String,
)

@Serializable
data class PracticeReplyRequestDto(
    val message: String,
)

@Serializable
data class PracticeScoreDto(
    val score: Int,
    val grammarScore: Int,
    val vocabularyScore: Int,
    val naturalness: Int,
    val mistakes: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
)

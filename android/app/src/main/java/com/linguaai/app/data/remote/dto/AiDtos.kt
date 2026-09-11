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
)

@Serializable
data class GenerateQuizRequestDto(
    val languageId: Long = 1,
    val level: String = "N3",
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

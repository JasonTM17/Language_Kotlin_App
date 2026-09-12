package com.linguaai.server.ai

import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.api.dto.ProfileDto
import com.linguaai.server.config.AppConfig
import com.linguaai.server.repository.AiRepository
import com.linguaai.server.repository.AuthRepository
import com.linguaai.server.repository.ContentRepository
import com.linguaai.server.db.UserMistakes
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.transactions.transaction

// ---- request/response DTOs for the AI area ----

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
data class ConversationDto(
    val id: Long,
    val title: String,
    val mode: String,
    val updatedAt: String? = null,
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
data class GenerateQuizRequestDto(
    val languageId: Long = 1,
    val level: String = "N3",
    val topic: String? = null,
    val count: Int = 5,
)

@Serializable
data class PracticeStartRequestDto(
    val scenario: String,
    val conversationId: Long? = null,
)

@Serializable
data class PracticeScoreDto(
    val score: Int,
    val grammarScore: Int,
    val vocabularyScore: Int,
    val naturalness: Int,
    val mistakes: List<String>,
    val recommendations: List<String>,
)

/**
 * AI gateway: rate limiting, prompt assembly, provider calls, persistence and
 * memory summarization all live behind these endpoints. Secrets stay here.
 */
class AiService(
    private val config: AppConfig,
    private val provider: AiProvider,
    private val aiRepository: AiRepository,
    private val authRepository: AuthRepository,
    private val contentRepository: ContentRepository,
    private val rateLimiter: AiRateLimiter,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun conversations(userId: Long): List<ConversationDto> =
        aiRepository.listConversations(userId).map {
            ConversationDto(id = it.id, title = it.title, mode = it.mode)
        }

    fun messages(userId: Long, conversationId: Long): List<AiMessageDto> {
        requireOwnership(userId, conversationId)
        return aiRepository.messages(conversationId).map {
            AiMessageDto(id = it.id, role = it.role, content = it.content)
        }
    }

    suspend fun chat(userId: Long, request: AiChatRequestDto): AiChatResponseDto {
        ensureRateLimit(userId)
        val profile = authRepository.findProfile(userId)
        val context = aiRepository.findConversation(request.conversationId ?: 0L, userId)

        val conversation = if (context != null) {
            context
        } else {
            aiRepository.createConversation(
                userId = userId,
                title = request.message.take(60),
                mode = request.mode,
                contextLessonId = request.contextLessonId,
                contextGrammarId = request.contextGrammarId,
            )
        }

        return exchange(userId, conversation, profile, request.message)
    }

    suspend fun explain(userId: Long, request: ExplainRequestDto): AiChatResponseDto {
        ensureRateLimit(userId)
        val profile = authRepository.findProfile(userId)
        val conversation = aiRepository.createConversation(
            userId = userId,
            title = "Explain: ${request.text?.take(TITLE_PREVIEW_LENGTH) ?: "grammar #${request.grammarId}"}",
            mode = "grammar-explain",
            contextLessonId = null,
            contextGrammarId = request.grammarId,
        )
        val question = request.text ?: "Please explain grammar point #${request.grammarId}."
        return exchange(userId, conversation, profile, question)
    }

    suspend fun correct(userId: Long, request: CorrectRequestDto): AiChatResponseDto {
        ensureRateLimit(userId)
        val profile = authRepository.findProfile(userId)
        val conversation = aiRepository.createConversation(
            userId = userId,
            title = "Correction: ${request.sentence.take(TITLE_PREVIEW_LENGTH)}",
            mode = "sentence-correction",
            contextLessonId = null,
            contextGrammarId = null,
        )
        // Corrected sentences double as mistake records for future context.
        transaction {
            UserMistakes.insert {
                it[UserMistakes.userId] = userId
                it[UserMistakes.topic] = request.sentence.take(MAX_TOPIC_LENGTH)
                it[UserMistakes.detail] = "Submitted for correction"
                it[UserMistakes.sourceType] = "CORRECTION"
                it[UserMistakes.createdAt] = java.time.LocalDateTime.now()
            }
        }
        return exchange(userId, conversation, profile, request.sentence)
    }

    suspend fun startPractice(userId: Long, request: PracticeStartRequestDto): AiChatResponseDto {
        ensureRateLimit(userId)
        val profile = authRepository.findProfile(userId)
        val conversation = aiRepository.createConversation(
            userId = userId,
            title = "Practice: ${request.scenario}",
            mode = "conversation-practice",
            contextLessonId = null,
            contextGrammarId = null,
        )
        return exchange(
            userId,
            conversation,
            profile,
            "Let's practice: $request.scenario. Please start the conversation.",
        )
    }

    suspend fun practiceReply(userId: Long, conversationId: Long, message: String): AiChatResponseDto {
        ensureRateLimit(userId)
        val conversation = requireOwnership(userId, conversationId)
        return exchange(userId, conversation, authRepository.findProfile(userId), message)
    }

    suspend fun scorePractice(userId: Long, conversationId: Long): PracticeScoreDto {
        ensureRateLimit(userId)
        val conversation = requireOwnership(userId, conversationId)
        val builder = promptBuilder(userId)
        val system = builder.systemPrompt(
            "practice-score",
            authRepository.findProfile(userId),
            null,
            null,
            conversation.summary,
        )
        val messages = builder.buildMessages(system, conversation.id, conversation.summarizedUntil) +
            AiMessage("user", "Score this conversation now. Return only the JSON object.")
        val response = provider.chat(
            AiChatRequest(messages = messages, temperature = 0.2, jsonMode = true, scenarioHint = "practice-score"),
        )
        return parsePracticeScore(response.content)
    }

    suspend fun generateQuiz(userId: Long, request: GenerateQuizRequestDto): GeneratedQuizDto {
        ensureRateLimit(userId)
        val profile = authRepository.findProfile(userId)
        val builder = promptBuilder(userId)
        val system = builder.systemPrompt("general", profile, null, null, null)
        val count = request.count.coerceIn(MIN_QUIZ_QUESTIONS, MAX_QUIZ_QUESTIONS)
        // Resolve the language name. The previous version interpolated the numeric
        // id ("language #1"), which conveys nothing to a model.
        val languageName = runCatching { contentRepository.findLanguageById(request.languageId) }
            .getOrNull()
            ?.name
            ?: "the learner's target language"
        val prompt = """
            Create $count multiple-choice questions for a ${request.level} learner of $languageName.
            ${request.topic?.let { "Focus on: $it." } ?: ""}
            Requirements:
            - Exactly one option is correct and the other three are plausible distractors at the same level, not obviously wrong.
            - Do not reuse the same distractor pattern across questions.
            - "explanation" states the rule or reason in one sentence, in English.
            Return STRICT JSON: {"questions":[{"prompt":...,"options":[4 strings],"correctAnswer":one option verbatim,"explanation":...}]}
        """.trimIndent()
        val response = provider.chat(
            AiChatRequest(
                messages = listOf(AiMessage("system", system), AiMessage("user", prompt)),
                temperature = 0.6,
                jsonMode = true,
            ),
        )
        return validateQuizPayload(response.content)
    }

    // ---- internals ----

    private suspend fun exchange(
        userId: Long,
        conversation: com.linguaai.server.repository.ConversationRow,
        profile: ProfileDto?,
        userText: String,
    ): AiChatResponseDto {
        val builder = promptBuilder(userId)
        val system = builder.systemPrompt(
            mode = conversation.mode,
            profile = profile,
            contextLessonId = conversation.contextLessonId,
            contextGrammarId = conversation.contextGrammarId,
            summary = conversation.summary,
        )
        val history = builder.buildMessages(system, conversation.id, conversation.summarizedUntil)

        aiRepository.addMessage(conversation.id, "USER", userText)
        val fullMessages = history + AiMessage("user", userText)

        val response = try {
            provider.chat(AiChatRequest(messages = fullMessages))
        } catch (e: AiProviderException) {
            // Carry the cause through the mapping, otherwise the provider's own
            // failure reason is lost at exactly the point it matters most.
            throw when (e.kind) {
                AiProviderException.Kind.RATE_LIMITED -> ApiException(
                    HttpStatusCode.TooManyRequests,
                    ErrorCodes.RATE_LIMITED,
                    "The AI tutor is busy. Please retry shortly.",
                    cause = e,
                )
                AiProviderException.Kind.TIMEOUT -> ApiException(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorCodes.AI_UNAVAILABLE,
                    "The AI tutor took too long to respond. Please try again.",
                    cause = e,
                )
                else -> ApiException(
                    HttpStatusCode.BadGateway,
                    ErrorCodes.AI_UNAVAILABLE,
                    "The AI tutor is unavailable right now.",
                    cause = e,
                )
            }
        }

        val reply = response.content
        if (reply.isBlank()) {
            throw ApiException(
                HttpStatusCode.BadGateway,
                ErrorCodes.AI_UNAVAILABLE,
                "The AI tutor returned an empty response.",
            )
        }
        aiRepository.addMessage(conversation.id, "ASSISTANT", reply)
        summarizeIfNeeded(conversation)
        return AiChatResponseDto(conversationId = conversation.id, reply = reply, mode = conversation.mode)
    }

    /**
     * Memory summarization (spec §17): once the unsummarized tail grows past a
     * threshold, fold the oldest half into the rolling summary and move the
     * watermark. Failures never break the chat turn.
     */
    private fun summarizeIfNeeded(conversation: com.linguaai.server.repository.ConversationRow) {
        try {
            val all = aiRepository.messages(conversation.id)
            val pending = all.filter { it.id > (conversation.summarizedUntil ?: 0L) }
            if (pending.size <= SUMMARIZE_THRESHOLD) return
            val evicted = pending.take(pending.size / 2)
            // foldSummary caps the total size; appending directly let the summary
            // grow without bound across a long conversation.
            val newSummary = PromptBuilder.foldSummary(
                existing = conversation.summary,
                addition = PromptBuilder.summarize(evicted),
            )
            aiRepository.updateSummary(conversation.id, newSummary, evicted.last().id)
        } catch (_: Exception) {
            // Summarization is best-effort; the chat flow continues.
        }
    }

    private fun promptBuilder(userId: Long): PromptBuilder {
        val topics = transaction {
            UserMistakes.selectAll()
                .andWhere { UserMistakes.userId eq userId }
                .orderBy(UserMistakes.id, org.jetbrains.exposed.sql.SortOrder.DESC)
                .limit(WEAK_TOPIC_LIMIT.toInt())
                .map { it[UserMistakes.topic] }
        }
        return PromptBuilder(aiRepository, contentRepository, topics)
    }

    private fun ensureRateLimit(userId: Long) {
        if (!rateLimiter.tryAcquire(userId)) {
            throw ApiException(
                HttpStatusCode.TooManyRequests,
                ErrorCodes.RATE_LIMITED,
                "AI rate limit reached (${config.aiRateLimitPerMinute}/minute). Please wait a moment.",
            )
        }
    }

    private fun requireOwnership(userId: Long, conversationId: Long): com.linguaai.server.repository.ConversationRow =
        aiRepository.findConversation(conversationId, userId)
            ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "Conversation not found")

    private fun validateQuizPayload(raw: String): GeneratedQuizDto = try {
        val payload = json.decodeFromString<GeneratedQuizDto>(raw.trim())
        require(payload.questions.isNotEmpty()) { "empty questions" }
        payload.questions.forEach { q ->
            require(q.prompt.isNotBlank()) { "blank prompt" }
            require(q.options.size in QUIZ_OPTIONS_RANGE) { "bad options size" }
            require(q.correctAnswer in q.options) { "correctAnswer must be one of options" }
        }
        payload
    } catch (e: Exception) {
        // Carry the cause. Without it a 502 gives the operator no trace of what
        // the provider actually returned or which require() rejected it.
        throw ApiException(
            HttpStatusCode.BadGateway,
            ErrorCodes.AI_UNAVAILABLE,
            "The AI tutor returned an invalid quiz. Please retry.",
            cause = e,
        )
    }

    private fun parsePracticeScore(raw: String): PracticeScoreDto = try {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        json.decodeFromString<PracticeScoreDto>(cleaned)
    } catch (e: Exception) {
        throw ApiException(
            HttpStatusCode.BadGateway,
            ErrorCodes.AI_UNAVAILABLE,
            "Could not score the conversation. Please retry.",
            cause = e,
        )
    }

    private companion object {
        const val SUMMARIZE_THRESHOLD = 20

        /** How much of the learner's text becomes an auto-generated title. */
        const val TITLE_PREVIEW_LENGTH = 40

        /**
         * Must match the width of user_mistakes.topic in db/Tables.kt. A longer
         * value fails the insert rather than being clipped, so the two are coupled.
         */
        const val MAX_TOPIC_LENGTH = 190

        /** Generated quiz size bounds. */
        const val MIN_QUIZ_QUESTIONS = 1
        const val MAX_QUIZ_QUESTIONS = 10

        /** Recurring mistake topics fed into the prompt as weak topics. */
        const val WEAK_TOPIC_LIMIT = 5

        /**
         * Options per generated question. Two is the minimum for a choice and
         * more than six stops being a usable question.
         */
        val QUIZ_OPTIONS_RANGE = 2..6
    }
}

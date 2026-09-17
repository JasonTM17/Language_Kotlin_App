package com.linguaai.server.ai

import com.linguaai.server.ai.rag.RagService
import com.linguaai.server.ai.rag.RetrievedChunk
import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.api.dto.ProfileDto
import com.linguaai.server.config.AppConfig
import com.linguaai.server.db.UserMistakes
import com.linguaai.server.repository.AiRepository
import com.linguaai.server.repository.AuthRepository
import com.linguaai.server.repository.ContentRepository
import com.linguaai.server.repository.ConversationRow
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

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
    /**
     * Corpus chunks grounded into this reply. Defaulted so older clients and
     * stored responses that omit the field keep decoding (additive contract).
     */
    val sources: List<AiSourceDto> = emptyList(),
)

/** One retrieval hit cited in the tutor reply (deep-link ready). */
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
data class KnowledgeSearchResponseDto(
    val query: String,
    val hits: List<AiSourceDto>,
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
    val conversationId: Long? = null,
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
    val languageId: Long? = null,
    val level: String? = null,
    val topic: String? = null,
    val count: Int = 5,
)

@Serializable
data class PracticeStartRequestDto(
    val scenario: String,
    val conversationId: Long? = null,
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
    private val ragService: RagService,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val log = LoggerFactory.getLogger(AiService::class.java)
    private val rateLimiter = AiRateLimiter(config.aiRateLimitPerMinute)

    fun conversations(userId: Long): List<ConversationDto> =
        aiRepository.listConversations(userId).map {
            ConversationDto(id = it.id, title = it.title, mode = it.mode)
        }

    fun messages(
        userId: Long,
        conversationId: Long,
    ): List<AiMessageDto> {
        requireOwnership(userId, conversationId)
        return aiRepository.messages(conversationId).map {
            AiMessageDto(id = it.id, role = it.role, content = it.content)
        }
    }

    suspend fun chat(
        userId: Long,
        request: AiChatRequestDto,
    ): AiChatResponseDto {
        val message = requireText(request.message, "message")
        ensureRateLimit(userId)
        val profile = authRepository.findProfile(userId)
        val context = aiRepository.findConversation(request.conversationId ?: 0L, userId)

        val conversation =
            if (context != null) {
                context
            } else {
                aiRepository.createConversation(
                    userId = userId,
                    title = message.take(60),
                    mode = request.mode,
                    contextLessonId = request.contextLessonId,
                    contextGrammarId = request.contextGrammarId,
                )
            }

        return exchange(
            userId = userId,
            conversation = conversation,
            profile = profile,
            turn = ExchangeTurn(userText = message, discardConversationOnFailure = context == null),
        )
    }

    suspend fun explain(
        userId: Long,
        request: ExplainRequestDto,
    ): AiChatResponseDto {
        ensureRateLimit(userId)
        val profile = authRepository.findProfile(userId)
        val conversation =
            aiRepository.createConversation(
                userId = userId,
                title = "Explain: ${request.text?.take(TITLE_PREVIEW_LENGTH) ?: "grammar #${request.grammarId}"}",
                mode = "grammar-explain",
                contextLessonId = null,
                contextGrammarId = request.grammarId,
            )
        val question = request.text ?: "Please explain grammar point #${request.grammarId}."
        return exchange(userId, conversation, profile, ExchangeTurn(question))
    }

    suspend fun correct(
        userId: Long,
        request: CorrectRequestDto,
    ): AiChatResponseDto {
        val sentence = requireText(request.sentence, "sentence")
        ensureRateLimit(userId)
        val profile = authRepository.findProfile(userId)
        val existingConversation =
            request.conversationId?.let {
                requireConversationMode(userId, it, "sentence-correction")
            }
        val conversation =
            existingConversation
                ?: aiRepository.createConversation(
                    userId = userId,
                    title = "Correction: ${sentence.take(TITLE_PREVIEW_LENGTH)}",
                    mode = "sentence-correction",
                    contextLessonId = null,
                    contextGrammarId = null,
                )
        val response =
            exchange(
                userId = userId,
                conversation = conversation,
                profile = profile,
                turn = ExchangeTurn(userText = sentence, discardConversationOnFailure = existingConversation == null),
            )
        // Corrected sentences double as mistake records for future context.
        transaction {
            UserMistakes.insert {
                it[UserMistakes.userId] = userId
                it[UserMistakes.topic] = sentence.take(MAX_TOPIC_LENGTH)
                it[UserMistakes.detail] = "Submitted for correction"
                it[UserMistakes.sourceType] = "CORRECTION"
                it[UserMistakes.createdAt] = java.time.LocalDateTime.now()
            }
        }
        return response
    }

    suspend fun startPractice(
        userId: Long,
        request: PracticeStartRequestDto,
    ): AiChatResponseDto {
        val scenario = requireText(request.scenario, "scenario")
        ensureRateLimit(userId)
        val profile = authRepository.findProfile(userId)
        val conversation =
            aiRepository.createConversation(
                userId = userId,
                title = "Practice: $scenario",
                mode = "conversation-practice",
                contextLessonId = null,
                contextGrammarId = null,
            )
        return exchange(
            userId,
            conversation,
            profile,
            ExchangeTurn(
                userText = scenario,
                providerUserText = "Let's practice: $scenario. Please start the conversation.",
                discardConversationOnFailure = true,
            ),
        )
    }

    suspend fun practiceReply(
        userId: Long,
        conversationId: Long,
        message: String,
    ): AiChatResponseDto {
        val normalizedMessage = requireText(message, "message")
        ensureRateLimit(userId)
        val conversation = requireConversationMode(userId, conversationId, "conversation-practice")
        return exchange(
            userId,
            conversation,
            authRepository.findProfile(userId),
            ExchangeTurn(normalizedMessage),
        )
    }

    suspend fun scorePractice(
        userId: Long,
        conversationId: Long,
    ): PracticeScoreDto {
        ensureRateLimit(userId)
        val conversation = requireConversationMode(userId, conversationId, "conversation-practice")
        val builder = promptBuilder(userId)
        val system =
            builder.systemPrompt(
                "practice-score",
                authRepository.findProfile(userId),
                FocusContext(),
                conversation.summary,
            )
        val messages =
            builder.buildMessages(system, conversation.id, conversation.summarizedUntil) +
                AiMessage("user", "Score this conversation now. Return only the JSON object.")
        val response =
            provider.chat(
                AiChatRequest(messages = messages, temperature = 0.2, jsonMode = true, scenarioHint = "practice-score"),
            )
        return parsePracticeScore(response.content)
    }

    suspend fun generateQuiz(
        userId: Long,
        request: GenerateQuizRequestDto,
    ): GeneratedQuizDto {
        ensureRateLimit(userId)
        val profile = authRepository.findProfile(userId)
        val builder = promptBuilder(userId)
        val system = builder.systemPrompt("general", profile, FocusContext(), null)
        val count = request.count.coerceIn(MIN_QUIZ_QUESTIONS, MAX_QUIZ_QUESTIONS)
        val languageId =
            request.languageId ?: profile?.languageId
                ?: throw ApiException(
                    HttpStatusCode.BadRequest,
                    ErrorCodes.VALIDATION,
                    "languageId is required until the learner profile is complete",
                )
        val level =
            request.level?.trim()?.takeIf { it.isNotEmpty() }
                ?: profile?.level?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw ApiException(
                    HttpStatusCode.BadRequest,
                    ErrorCodes.VALIDATION,
                    "level is required until the learner profile is complete",
                )
        // Resolve the language name. The previous version interpolated the numeric
        // id ("language #1"), which conveys nothing to a model.
        val languageName =
            contentRepository.findLanguageById(languageId)?.name
                ?: throw ApiException(
                    HttpStatusCode.BadRequest,
                    ErrorCodes.VALIDATION,
                    "Unknown languageId",
                )
        val prompt =
            """
            Create $count multiple-choice questions for a $level learner of $languageName.
            ${request.topic?.let { "Focus on: $it." } ?: ""}
            Requirements:
            - Exactly one option is correct and the other three are plausible distractors at the same level, not obviously wrong.
            - Do not reuse the same distractor pattern across questions.
            - "explanation" states the rule or reason in one sentence, in English.
            Return STRICT JSON: {"questions":[{"prompt":...,"options":[4 strings],"correctAnswer":one option verbatim,"explanation":...}]}
            """.trimIndent()
        val response =
            provider.chat(
                AiChatRequest(
                    messages = listOf(AiMessage("system", system), AiMessage("user", prompt)),
                    temperature = 0.6,
                    jsonMode = true,
                    // The mock provider keys on this hint to produce a quiz
                    // about the requested language instead of its built-in
                    // Japanese fixture.
                    scenarioHint = "quiz:$languageName",
                ),
            )
        return validateQuizPayload(response.content)
    }

    /**
     * Raw retrieval over the course corpus, for demos, the E2E harness and
     * client-side "related course content" surfaces. Rate limited like chat
     * because it triggers the same candidate load.
     */
    suspend fun searchKnowledge(
        userId: Long,
        query: String,
        level: String? = null,
        limit: Int = DEFAULT_SEARCH_LIMIT,
    ): KnowledgeSearchResponseDto {
        ensureRateLimit(userId)
        val trimmed = requireText(query, "query")
        val profile = authRepository.findProfile(userId)
        val languageId =
            profile?.languageId
                ?: throw ApiException(
                    HttpStatusCode.BadRequest,
                    ErrorCodes.VALIDATION,
                    "Set your learning language before searching the knowledge base",
                )
        val hits =
            try {
                ragService
                    .retrieve(trimmed, languageId, level ?: profile.level)
                    .take(limit.coerceIn(1, MAX_SEARCH_LIMIT))
            } catch (failure: Exception) {
                log.warn("Knowledge search failed; returning no hits", failure)
                emptyList()
            }
        return KnowledgeSearchResponseDto(query = trimmed, hits = hits.map { it.toSourceDto() })
    }

    // ---- internals ----

    /**
     * Retrieval grounding for one tutor turn: the fenced knowledge block for
     * the system prompt plus the cited chunks for the response. Any retrieval
     * failure degrades to an ungrounded turn — grounding must never turn a
     * working tutor into a 500.
     */
    private suspend fun groundingFor(
        mode: String,
        profile: ProfileDto?,
        query: String,
    ): Grounding {
        if (!config.ragEnabled) return Grounding.EMPTY
        if (mode !in RAG_MODES) return Grounding.EMPTY
        val languageId = profile?.languageId ?: return Grounding.EMPTY
        return try {
            val chunks = ragService.retrieve(query, languageId, profile.level)
            Grounding(context = ragService.formatContext(chunks), chunks = chunks)
        } catch (failure: Exception) {
            log.warn("RAG retrieval failed; continuing without knowledge context", failure)
            Grounding.EMPTY
        }
    }

    private class Grounding(
        val context: String?,
        val chunks: List<RetrievedChunk>,
    ) {
        companion object {
            val EMPTY = Grounding(context = null, chunks = emptyList())
        }
    }

    private fun RetrievedChunk.toSourceDto(): AiSourceDto =
        AiSourceDto(
            title = ref.title,
            sourceType = ref.sourceType,
            sourceId = ref.sourceId,
            chunkIndex = ref.chunkIndex,
            level = ref.level,
            score = score,
        )

    private suspend fun exchange(
        userId: Long,
        conversation: ConversationRow,
        profile: ProfileDto?,
        turn: ExchangeTurn,
    ): AiChatResponseDto {
        val builder = promptBuilder(userId)
        val grounding = groundingFor(conversation.mode, profile, turn.userText)
        val system =
            builder.systemPrompt(
                mode = conversation.mode,
                profile = profile,
                focus = FocusContext(conversation.contextLessonId, conversation.contextGrammarId),
                summary = conversation.summary,
                knowledge = grounding.context,
            )
        val history = builder.buildMessages(system, conversation.id, conversation.summarizedUntil)

        val fullMessages = history + AiMessage("user", turn.providerUserText)

        val reply =
            try {
                val response =
                    try {
                        provider.chat(AiChatRequest(messages = fullMessages))
                    } catch (e: AiProviderException) {
                        // Carry the cause through the mapping, otherwise the provider's own
                        // failure reason is lost at exactly the point it matters most.
                        throw when (e.kind) {
                            AiProviderException.Kind.RATE_LIMITED ->
                                ApiException(
                                    HttpStatusCode.TooManyRequests,
                                    ErrorCodes.RATE_LIMITED,
                                    "The AI tutor is busy. Please retry shortly.",
                                    cause = e,
                                )
                            AiProviderException.Kind.TIMEOUT ->
                                ApiException(
                                    HttpStatusCode.ServiceUnavailable,
                                    ErrorCodes.AI_UNAVAILABLE,
                                    "The AI tutor took too long to respond. Please try again.",
                                    cause = e,
                                )
                            else ->
                                ApiException(
                                    HttpStatusCode.BadGateway,
                                    ErrorCodes.AI_UNAVAILABLE,
                                    "The AI tutor is unavailable right now.",
                                    cause = e,
                                )
                        }
                    }

                response.content.takeIf { it.isNotBlank() }
                    ?: throw ApiException(
                        HttpStatusCode.BadGateway,
                        ErrorCodes.AI_UNAVAILABLE,
                        "The AI tutor returned an empty response.",
                    )
            } catch (failure: Exception) {
                if (turn.discardConversationOnFailure) {
                    aiRepository.deleteConversation(conversation.id)
                }
                throw failure
            }

        aiRepository.addExchange(conversation.id, turn.userText, reply)
        summarizeIfNeeded(conversation)
        return AiChatResponseDto(
            conversationId = conversation.id,
            reply = reply,
            mode = conversation.mode,
            sources = grounding.chunks.map { it.toSourceDto() },
        )
    }

    /**
     * Memory summarization (spec §17): once the unsummarized tail grows past a
     * threshold, fold the oldest half into the rolling summary and move the
     * watermark. Failures never break the chat turn.
     */
    private fun summarizeIfNeeded(conversation: ConversationRow) {
        try {
            val all = aiRepository.messages(conversation.id)
            val pending = all.filter { it.id > (conversation.summarizedUntil ?: 0L) }
            if (pending.size <= SUMMARIZE_THRESHOLD) return
            val evicted = pending.take(pending.size / 2)
            // foldSummary caps the total size; appending directly let the summary
            // grow without bound across a long conversation.
            val newSummary =
                PromptBuilder.foldSummary(
                    existing = conversation.summary,
                    addition = PromptBuilder.summarize(evicted),
                )
            aiRepository.updateSummary(conversation.id, newSummary, evicted.last().id)
        } catch (_: Exception) {
            // Summarization is best-effort; the chat flow continues.
        }
    }

    private data class ExchangeTurn(
        val userText: String,
        val providerUserText: String = userText,
        val discardConversationOnFailure: Boolean = false,
    )

    private fun promptBuilder(userId: Long): PromptBuilder {
        val topics =
            transaction {
                UserMistakes
                    .selectAll()
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

    private fun requireText(
        value: String,
        field: String,
    ): String =
        value.trim().takeIf { it.isNotEmpty() }
            ?: throw ApiException(
                HttpStatusCode.BadRequest,
                ErrorCodes.VALIDATION,
                "$field must not be blank",
            )

    private fun requireOwnership(
        userId: Long,
        conversationId: Long,
    ): ConversationRow =
        aiRepository.findConversation(conversationId, userId)
            ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "Conversation not found")

    private fun requireConversationMode(
        userId: Long,
        conversationId: Long,
        expectedMode: String,
    ): ConversationRow {
        val conversation = requireOwnership(userId, conversationId)
        if (conversation.mode != expectedMode) {
            throw ApiException(
                HttpStatusCode.BadRequest,
                ErrorCodes.VALIDATION,
                "Conversation is not a $expectedMode conversation",
            )
        }
        return conversation
    }

    private fun validateQuizPayload(raw: String): GeneratedQuizDto =
        try {
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

    private fun parsePracticeScore(raw: String): PracticeScoreDto =
        try {
            val cleaned =
                raw
                    .trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            json.decodeFromString<PracticeScoreDto>(cleaned).also { score ->
                require(score.score in SCORE_RANGE) { "score is outside 0..100" }
                require(score.grammarScore in SCORE_RANGE) { "grammarScore is outside 0..100" }
                require(score.vocabularyScore in SCORE_RANGE) { "vocabularyScore is outside 0..100" }
                require(score.naturalness in SCORE_RANGE) { "naturalness is outside 0..100" }
            }
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

        /**
         * Tutor modes that receive retrieved corpus context. Practice and
         * scoring modes are deliberately excluded: role-play turns should not
         * be seeded with reference excerpts.
         */
        val RAG_MODES = setOf("general", "grammar-explain")

        const val DEFAULT_SEARCH_LIMIT = 5
        const val MAX_SEARCH_LIMIT = 20

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

        /** Every practice score exposed to the learner uses the same 0–100 scale. */
        val SCORE_RANGE = 0..100
    }
}

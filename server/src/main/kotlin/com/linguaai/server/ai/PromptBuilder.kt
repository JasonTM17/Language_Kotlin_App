package com.linguaai.server.ai

import com.linguaai.server.api.dto.ProfileDto
import com.linguaai.server.repository.AiRepository
import com.linguaai.server.repository.ContentRepository
import com.linguaai.server.repository.MessageRow

/**
 * Builds the system prompt from trusted server-side data only:
 * learner profile, lesson/grammar context, weak topics and the rolling
 * conversation summary. The client never supplies system instructions
 * (prompt-injection defense, master spec §38).
 */
class PromptBuilder(
    private val aiRepository: AiRepository,
    private val contentRepository: ContentRepository,
    private val mistakeTopics: List<String>,
) {

    fun systemPrompt(
        mode: String,
        profile: ProfileDto?,
        contextLessonId: Long?,
        contextGrammarId: Long?,
        summary: String?,
    ): String = buildString {
        append("You are LinguaAI, a patient personal language tutor inside a learning app.\n")
        append("Reply in clear, simple English, inserting target-language examples where helpful.\n")

        if (profile != null) {
            append("\n[Learner profile]\n")
            append("- Level: ${profile.level ?: "unspecified"}\n")
            append("- Goal: ${profile.goal ?: "general improvement"}\n")
            append("- Daily target: ${profile.dailyGoalMinutes} minutes\n")
        }

        if (contextGrammarId != null) {
            runCatching { contentRepository.findGrammarById(contextGrammarId) }.getOrNull()?.let { grammar ->
                append("\n[Grammar context]\n")
                append("- Title: ${grammar.title}\n")
                append("- Structure: ${grammar.structure.orEmpty()}\n")
                append("- Meaning: ${grammar.meaning.orEmpty()}\n")
            }
        }
        if (contextLessonId != null) {
            runCatching { contentRepository.findLessonById(contextLessonId) }.getOrNull()?.let { lesson ->
                append("\n[Lesson context]\n")
                append("- ${lesson.title} (${lesson.level}, ${lesson.type})\n")
            }
        }

        if (mistakeTopics.isNotEmpty()) {
            append("\n[Weak topics — weave gentle review into explanations]\n")
            mistakeTopics.take(5).forEach { topic -> append("- $topic\n") }
        }

        if (!summary.isNullOrBlank()) {
            append("\n[Conversation so far]\n$summary\n")
        }

        append("\n[Mode]\n${modeInstruction(mode)}\n")
    }

    private fun modeInstruction(mode: String): String = when (mode) {
        "grammar-explain" -> "Explain the requested grammar point: meaning, structure, usage, two examples, common mistakes and one mini quiz question."
        "sentence-correction" -> "Correct the learner's sentence: show the corrected version, explain each fix briefly, and give one similar example."
        "conversation-practice" -> "Role-play the scenario naturally in the target language at the learner's level, one turn at a time, correcting nothing mid-conversation."
        "practice-score" -> "Return STRICT JSON: {\"score\":0-100,\"grammarScore\":0-100,\"vocabularyScore\":0-100,\"naturalness\":0-100,\"mistakes\":[...],\"recommendations\":[...]}."
        "mistakes-review" -> "The learner just finished a quiz. Explain their weak topics one by one with one worked example each."
        else -> "Answer as a general tutor, keeping replies compact and level-appropriate."
    }

    /**
     * Bounded context window: rolling summary + messages after the summary
     * watermark. Keeps token cost flat for long conversations (spec §17).
     */
    fun buildMessages(
        system: String,
        conversationId: Long,
        summarizedUntil: Long?,
        recentLimit: Int = 12,
    ): List<AiMessage> {
        val history = aiRepository.messages(conversationId, limit = 200)
        val recent = history
            .filter { it.id > (summarizedUntil ?: 0L) }
            .takeLast(recentLimit)
        return listOf(AiMessage("system", system)) +
            recent.map { AiMessage(role = it.role.lowercase(), content = it.content) }
    }

    companion object {
        /** Extractive rolling summary of the messages being evicted. */
        fun summarize(evicted: List<MessageRow>): String =
            evicted.takeLast(SUMMARY_WINDOW).joinToString("\n") { message ->
                val prefix = if (message.role.equals("user", true)) "Learner" else "Tutor"
                "$prefix: ${message.content.take(120)}"
            }

        private const val SUMMARY_WINDOW = 10
    }
}

package com.linguaai.app.domain.model

/** Type and static metadata for a daily quest. */
enum class DailyQuestType(
    val id: String,
    val target: Int,
    val xpReward: Int,
    val emoji: String,
) {
    AI_CHAT("ai_chat", 1, 40, "💬"),
    FLASHCARDS("flashcards", 10, 30, "🃏"),
    QUIZ("quiz", 1, 50, "⚡"),
    WORD_OF_DAY("word_of_day", 1, 20, "📖"),
}

/** Dynamic daily state for one quest. */
data class DailyQuest(
    val type: DailyQuestType,
    val progress: Int,
    val isClaimed: Boolean,
) {
    val isCompleted: Boolean get() = progress >= type.target
    val progressFraction: Float get() = (progress.toFloat() / type.target.toFloat()).coerceIn(0f, 1f)
}

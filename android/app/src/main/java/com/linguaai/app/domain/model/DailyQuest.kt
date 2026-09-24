package com.linguaai.app.domain.model

private const val SINGLE_ACTION_QUEST_TARGET = 1
private const val FLASHCARDS_QUEST_TARGET = 10
private const val AI_CHAT_QUEST_XP_REWARD = 40
private const val FLASHCARDS_QUEST_XP_REWARD = 30
private const val QUIZ_QUEST_XP_REWARD = 50
private const val WORD_OF_DAY_QUEST_XP_REWARD = 20

/** Type and static metadata for a daily quest. */
enum class DailyQuestType(
    val id: String,
    val target: Int,
    val xpReward: Int,
    val emoji: String,
) {
    AI_CHAT("ai_chat", SINGLE_ACTION_QUEST_TARGET, AI_CHAT_QUEST_XP_REWARD, "💬"),
    FLASHCARDS("flashcards", FLASHCARDS_QUEST_TARGET, FLASHCARDS_QUEST_XP_REWARD, "🃏"),
    QUIZ("quiz", SINGLE_ACTION_QUEST_TARGET, QUIZ_QUEST_XP_REWARD, "⚡"),
    WORD_OF_DAY("word_of_day", SINGLE_ACTION_QUEST_TARGET, WORD_OF_DAY_QUEST_XP_REWARD, "📖"),
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

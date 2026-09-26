package com.linguaai.app.ui.navigation

import com.linguaai.app.domain.model.DailyQuestType
import com.linguaai.app.domain.model.VocabularyCard
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes. Objects are top-level destinations (bottom bar),
 * classes carry arguments.
 */
@Serializable
data object SplashRoute

@Serializable
data object LoginRoute

@Serializable
data object RegisterRoute

@Serializable
data object OnboardingRoute

@Serializable
data object HomeRoute

@Serializable
data object LearnRoute

@Serializable
data object AiTutorRoute

@Serializable
data object ProgressRoute

@Serializable
data object ProfileRoute

@Serializable
data class LessonDetailRoute(
    val lessonId: Long,
)

@Serializable
data object VocabularyRoute

@Serializable
data object SavedWordsRoute

@Serializable
data object FlashcardRoute

@Serializable
data object ListenAndTypeRoute

@Serializable
data object GrammarRoute

@Serializable
data class GrammarDetailRoute(
    val grammarId: Long,
)

@Serializable
data class QuizRoute(
    val quizId: Long,
)

@Serializable
data class AiChatRoute(
    val conversationId: Long? = null,
    val mode: String = "general",
    val seed: String? = null,
)

/** Existing typed destination selected by an unfinished Daily Quest. */
internal fun dailyQuestDestination(
    questType: DailyQuestType,
    wordOfDay: VocabularyCard?,
): Any =
    when (questType) {
        DailyQuestType.AI_CHAT -> AiChatRoute(mode = "general")
        DailyQuestType.FLASHCARDS -> FlashcardRoute
        DailyQuestType.QUIZ -> LearnRoute
        DailyQuestType.WORD_OF_DAY ->
            wordOfDay?.let { AiChatRoute(mode = "general", seed = wordOfDayAiSeed(it)) } ?: VocabularyRoute
    }

internal fun wordOfDayAiSeed(word: VocabularyCard): String =
    "How do I naturally use the word '${word.word}' (${word.meaning}) in conversation? " +
        "Give me 2 example sentences."

/** Destinations that show the bottom navigation bar. */
val topLevelDestinations = listOf(HomeRoute, LearnRoute, AiTutorRoute, ProgressRoute, ProfileRoute)

package com.linguaai.app.ui.navigation

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
)

/** Destinations that show the bottom navigation bar. */
val topLevelDestinations = listOf(HomeRoute, LearnRoute, AiTutorRoute, ProgressRoute, ProfileRoute)

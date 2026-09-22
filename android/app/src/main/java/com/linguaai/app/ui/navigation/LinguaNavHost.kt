package com.linguaai.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.linguaai.app.ui.components.LinguaBottomBar
import com.linguaai.app.ui.screens.auth.LoginScreen
import com.linguaai.app.ui.screens.auth.RegisterScreen
import com.linguaai.app.ui.screens.flashcard.FlashcardScreen
import com.linguaai.app.ui.screens.grammar.GrammarDetailScreen
import com.linguaai.app.ui.screens.grammar.GrammarScreen
import com.linguaai.app.ui.screens.home.HomeScreen
import com.linguaai.app.ui.screens.learn.LearnScreen
import com.linguaai.app.ui.screens.learn.LessonDetailScreen
import com.linguaai.app.ui.screens.onboarding.OnboardingScreen
import com.linguaai.app.ui.screens.quiz.QuizScreen
import com.linguaai.app.ui.screens.splash.SplashScreen
import com.linguaai.app.ui.screens.splash.StartDestination
import com.linguaai.app.ui.screens.vocabulary.VocabularyScreen
import kotlin.reflect.KClass

/** Short cross-fade between destinations; long enough to read as a transition. */
private const val NAV_TRANSITION_MILLIS = 200

/**
 * Single navigation host for the app. The bottom bar is shown only on
 * top-level destinations; detail screens get the full canvas.
 */
@Composable
fun LinguaNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar =
        topLevelDestinations.any { route ->
            currentDestination?.hasRoute(route::class) == true
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                LinguaBottomBar(
                    currentDestination = currentDestination,
                    onNavigate = { routeClass -> navController.navigateToTopLevel(routeClass) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SplashRoute,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(NAV_TRANSITION_MILLIS)) },
            exitTransition = { fadeOut(animationSpec = tween(NAV_TRANSITION_MILLIS)) },
        ) {
            entryGraph(navController)
            dashboardGraph(navController)
            aiGraph(navController)
            catalogueGraph(navController)
        }
    }
}

/**
 * Sign-in path: splash decides where to land, and neither auth screen can be
 * returned to once it has been passed.
 */
private fun NavGraphBuilder.entryGraph(navController: NavHostController) {
    composable<SplashRoute> {
        SplashScreen(
            onLanding = { destination ->
                val target =
                    when (destination) {
                        StartDestination.LOGIN -> LoginRoute
                        StartDestination.ONBOARDING -> OnboardingRoute
                        StartDestination.HOME -> HomeRoute
                    }
                navController.navigate(target) {
                    popUpTo(SplashRoute) { inclusive = true }
                }
            },
        )
    }

    composable<LoginRoute> {
        LoginScreen(
            onNavigateToRegister = {
                navController.navigate(RegisterRoute) { launchSingleTop = true }
            },
            onAuthenticated = { onboarded ->
                navController.navigate(if (onboarded) HomeRoute else OnboardingRoute) {
                    popUpTo(LoginRoute) { inclusive = true }
                }
            },
        )
    }
    composable<RegisterRoute> {
        RegisterScreen(
            onNavigateToLogin = {
                navController.navigate(LoginRoute) { launchSingleTop = true }
            },
            onRegistered = {
                navController.navigate(OnboardingRoute) {
                    popUpTo(RegisterRoute) { inclusive = true }
                }
            },
        )
    }
    composable<OnboardingRoute> {
        OnboardingScreen(
            onCompleted = {
                navController.navigate(HomeRoute) {
                    popUpTo(OnboardingRoute) { inclusive = true }
                }
            },
        )
    }
}

/** The four tab destinations plus the dashboard they lead into. */
private fun NavGraphBuilder.dashboardGraph(navController: NavHostController) {
    composable<HomeRoute> {
        HomeScreen(
            onContinueLesson = { lessonId ->
                navController.navigate(LessonDetailRoute(lessonId))
            },
            onStartReview = {
                navController.navigate(FlashcardRoute)
            },
            onOpenAiTutor = {
                navController.navigate(AiTutorRoute) {
                    popUpTo(HomeRoute) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onOpenVocabulary = {
                navController.navigate(VocabularyRoute) { launchSingleTop = true }
            },
        )
    }
    composable<LearnRoute> {
        LearnScreen(
            onOpenLesson = { lessonId -> navController.navigate(LessonDetailRoute(lessonId)) },
            onOpenVocabulary = { navController.navigate(VocabularyRoute) },
            onOpenGrammar = { navController.navigate(GrammarRoute) },
            onOpenFlashcards = { navController.navigate(FlashcardRoute) },
            onStartQuiz = { quizId -> navController.navigate(QuizRoute(quizId)) },
        )
    }
    composable<ProgressRoute> {
        com.linguaai.app.ui.screens.progress
            .ProgressScreen()
    }
    composable<ProfileRoute> {
        com.linguaai.app.ui.screens.profile.ProfileScreen(
            onSignedOut = {
                // Clear the whole back stack: after sign-out the previous
                // user's screens must not be reachable with Back.
                navController.navigate(LoginRoute) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
}

/**
 * AI Tutor entry and the chat surface every specialized mode shares.
 *
 * `AiChatRoute` is registered exactly once. A second registration used to sit
 * in the catalogue graph rendering a placeholder, and because the last
 * registration wins it silently replaced the real chat screen.
 */
private fun NavGraphBuilder.aiGraph(navController: NavHostController) {
    composable<AiTutorRoute> {
        com.linguaai.app.ui.screens.ai.AiHomeScreen(
            onOpenConversation = { conversationId, mode ->
                val routeMode = chatRouteMode(conversationId, mode)
                navController.navigate(AiChatRoute(conversationId = conversationId, mode = routeMode))
            },
        )
    }
    composable<AiChatRoute> { entry ->
        com.linguaai.app.ui.screens.ai
            .AiChatScreen(
                onBack = { navController.popBackStack() },
                onOpenSource = { source ->
                    sourceRouteFor(source)?.let { navController.navigate(it) }
                },
            )
    }
}

/** Lesson, vocabulary, flashcard, grammar and quiz destinations. */
private fun NavGraphBuilder.catalogueGraph(navController: NavHostController) {
    composable<LessonDetailRoute> { entry ->
        LessonDetailScreen(
            onBack = { navController.popBackStack() },
            onAskAi = { lessonId ->
                navController.navigate(AiChatRoute(conversationId = lessonId, mode = "lesson-context"))
            },
        )
    }
    composable<VocabularyRoute> {
        VocabularyScreen(
            onBack = { navController.popBackStack() },
            onOpenSavedWords = { navController.navigate(SavedWordsRoute) },
        )
    }
    composable<SavedWordsRoute> {
        com.linguaai.app.ui.screens.vocabulary.saved.SavedWordsScreen(onBack = { navController.popBackStack() })
    }
    composable<FlashcardRoute> {
        FlashcardScreen(onBack = { navController.popBackStack() })
    }
    composable<GrammarRoute> {
        GrammarScreen(
            onOpenGrammar = { grammarId -> navController.navigate(GrammarDetailRoute(grammarId)) },
        )
    }
    composable<GrammarDetailRoute> { entry ->
        GrammarDetailScreen(
            onBack = { navController.popBackStack() },
            onAskAi = { grammarId ->
                navController.navigate(AiChatRoute(conversationId = grammarId, mode = "grammar-explain"))
            },
        )
    }
    composable<QuizRoute> { entry ->
        QuizScreen(
            onBack = { navController.popBackStack() },
            onAskAiAboutMistakes = { quizId ->
                navController.navigate(AiChatRoute(conversationId = quizId, mode = "mistakes"))
            },
        )
    }
}

/**
 * Where a tutor citation leads. Retrieval tags each hit with the id from its
 * own content table, so a LESSON hit carries a `Lessons.id` and a GRAMMAR hit a
 * `GrammarLessons.id` — the same namespaces the detail routes take. Vocabulary
 * has no id-carrying destination, so its citations stay inert rather than
 * navigating somewhere wrong.
 */
internal fun sourceRouteFor(source: com.linguaai.app.data.remote.dto.AiSourceDto): Any? =
    when (source.sourceType) {
        "LESSON" -> LessonDetailRoute(source.sourceId)
        "GRAMMAR" -> GrammarDetailRoute(source.sourceId)
        else -> null
    }

internal fun chatRouteMode(
    conversationId: Long?,
    mode: String,
): String =
    if (conversationId != null && mode !in SPECIALIZED_HISTORY_MODES) {
        "conversation"
    } else {
        mode
    }

private val SPECIALIZED_HISTORY_MODES = setOf("conversation-practice", "sentence-correction")

/** Tab navigation preserves each tab's back stack state. */
private fun NavHostController.navigateToTopLevel(routeClass: KClass<*>) {
    val options =
        navOptions {
            popUpTo(HomeRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    when (routeClass) {
        HomeRoute::class -> navigate(HomeRoute, options)
        LearnRoute::class -> navigate(LearnRoute, options)
        AiTutorRoute::class -> navigate(AiTutorRoute, options)
        ProgressRoute::class -> navigate(ProgressRoute, options)
        ProfileRoute::class -> navigate(ProfileRoute, options)
    }
}

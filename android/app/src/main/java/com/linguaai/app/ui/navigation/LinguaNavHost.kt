package com.linguaai.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
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
import androidx.navigation.toRoute
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
import com.linguaai.app.ui.screens.placeholders.PlaceholderScreen
import com.linguaai.app.ui.screens.quiz.QuizScreen
import com.linguaai.app.ui.screens.vocabulary.VocabularyScreen
import com.linguaai.app.ui.screens.splash.SplashScreen
import com.linguaai.app.ui.screens.splash.StartDestination
import kotlin.reflect.KClass

/**
 * Single navigation host for the app. The bottom bar is shown only on
 * top-level destinations; detail screens get the full canvas.
 */
@Composable
fun LinguaNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { route ->
        currentDestination?.hasRoute(route::class) == true
    }

    Scaffold(
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
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
        ) {
            composable<SplashRoute> {
                SplashScreen(
                    onLanding = { destination ->
                        val target = when (destination) {
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
                    onAuthenticated = {
                        navController.navigate(OnboardingRoute) {
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
            composable<AiTutorRoute> {
                PlaceholderScreen(title = "AI Tutor")
            }
            composable<ProgressRoute> {
                PlaceholderScreen(title = "Progress")
            }
            composable<ProfileRoute> {
                PlaceholderScreen(title = "Profile")
            }

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
                    onOpenFlashcards = { navController.navigate(FlashcardRoute) },
                )
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
            composable<QuizResultRoute> { entry ->
                val route = entry.toRoute<QuizResultRoute>()
                PlaceholderScreen(title = "Quiz result #${route.attemptId}")
            }
            composable<AiChatRoute> { entry ->
                val route = entry.toRoute<AiChatRoute>()
                PlaceholderScreen(title = "AI chat (${route.mode})")
            }
        }
    }
}

/** Tab navigation preserves each tab's back stack state. */
private fun NavHostController.navigateToTopLevel(routeClass: KClass<*>) {
    val options = navOptions {
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

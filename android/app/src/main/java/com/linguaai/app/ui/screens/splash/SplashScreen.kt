package com.linguaai.app.ui.screens.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.ui.components.LinguaMascot
import com.linguaai.app.ui.theme.BrandGradients
import com.linguaai.app.ui.theme.LinguaMotion
import com.linguaai.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps the brand mark on screen long enough to register as intentional. */
private const val SPLASH_DELAY_MILLIS = 600L

/** Pop-in start scale for the mascot; springs to full size once visible. */
private const val SPLASH_START_SCALE = 0.6f

/**
 * Brand splash that resolves where the app should land: an active session goes
 * Home (or Onboarding), otherwise the login entry point. The full-bleed hero
 * gradient with a spring pop-in gives the wordmark a single confident moment.
 */
@Composable
fun SplashScreen(
    onLanding: (StartDestination) -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()

    var delayElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPLASH_DELAY_MILLIS)
        delayElapsed = true
    }
    LaunchedEffect(startDestination, delayElapsed) {
        if (delayElapsed) {
            startDestination?.let(onLanding)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BrandGradients.hero()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var appeared by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (appeared) 1f else SPLASH_START_SCALE,
            animationSpec = LinguaMotion.pop(),
            label = "splashScale",
        )
        val alpha by animateFloatAsState(
            targetValue = if (appeared) 1f else 0f,
            animationSpec = LinguaMotion.medium(),
            label = "splashAlpha",
        )
        LaunchedEffect(Unit) {
            launch { appeared = true }
        }
        LinguaMascot(
            contentDescription = stringResource(R.string.mascot_content_description),
            mascotSize = 132.dp,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = BrandGradients.OnHero,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = stringResource(R.string.splash_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = BrandGradients.OnHeroMuted,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

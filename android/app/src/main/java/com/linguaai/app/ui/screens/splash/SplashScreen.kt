package com.linguaai.app.ui.screens.splash

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.ui.components.LinguaMascot
import com.linguaai.app.ui.theme.Spacing
import kotlinx.coroutines.delay

/** Keeps the brand mark on screen long enough to register as intentional. */
private const val SPLASH_DELAY_MILLIS = 600L

/**
 * Brand splash that resolves where the app should land: an active session goes
 * Home (or Onboarding), otherwise the login entry point.
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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinguaMascot(
            contentDescription = stringResource(R.string.mascot_content_description),
            mascotSize = 128.dp,
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = stringResource(R.string.splash_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

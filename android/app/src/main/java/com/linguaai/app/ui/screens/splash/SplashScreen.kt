package com.linguaai.app.ui.screens.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    LaunchedEffect(Unit) { delay(SPLASH_DELAY_MILLIS) }
    LaunchedEffect(startDestination) {
        startDestination?.let(onLanding)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.SmartToy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = "LinguaAI",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = "Learn smarter, every day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

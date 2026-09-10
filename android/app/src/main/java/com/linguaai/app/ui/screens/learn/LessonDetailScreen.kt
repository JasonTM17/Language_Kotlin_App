package com.linguaai.app.ui.screens.learn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.ui.components.ErrorState
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailScreen(
    onBack: () -> Unit,
    onAskAi: (Long) -> Unit,
    viewModel: LessonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.completed) {
        if (state.completed) snackbar.showSnackbar("Lesson completed — progress will sync.")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.lesson?.title ?: "Lesson") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator()
            state.lesson == null -> ErrorState(
                message = state.error ?: "Lesson unavailable",
                retryLabel = "Retry",
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md),
            ) {
                Text(
                    text = state.lesson?.description.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.lesson?.content.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                LinguaButton(
                    text = if (state.completed) "Completed ✓" else "Mark as learned",
                    onClick = viewModel::markCompleted,
                    enabled = !state.completed,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
                LinguaButton(
                    text = "Ask AI about this lesson",
                    onClick = { onAskAi(state.lesson?.id ?: 0) },
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(32.dp))
            }
        }
    }
}

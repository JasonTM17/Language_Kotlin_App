package com.linguaai.app.ui.screens.flashcard

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.domain.srs.ReviewGrade
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    onBack: () -> Unit,
    viewModel: FlashcardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review words") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.padding(padding))
            state.finished ->
                EmptyState(
                    title =
                        when {
                            state.error != null -> "Review is paused"
                            state.reviewedCount > 0 -> "Session complete"
                            else -> "Nothing due right now"
                        },
                    message =
                        state.error
                            ?: if (state.reviewedCount > 0) {
                                "You reviewed ${state.reviewedCount} words. Come back later for the next batch."
                            } else {
                                "All caught up. New words unlock as review times arrive."
                            },
                    actionLabel = if (state.error != null) "Back" else "Done",
                    onAction = onBack,
                    modifier = Modifier.padding(padding),
                )
            else -> FlashcardContent(state, viewModel::onEvent, Modifier.padding(padding))
        }
    }
}

@Composable
private fun FlashcardContent(
    state: FlashcardUiState,
    onEvent: (FlashcardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = state.current ?: return
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Text(
            text = "Card ${state.currentIndex + 1} of ${state.queue.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LinguaCard(
            modifier = Modifier.padding(top = Spacing.md),
            containerColor =
                if (state.isRevealed) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
        ) {
            AnimatedContent(targetState = state.isRevealed, label = "flashcard") { revealed ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xl),
                ) {
                    Text(
                        text = card.word,
                        style = MaterialTheme.typography.displaySmall,
                        color =
                            if (revealed) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                    )
                    card.reading?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color =
                                if (revealed) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    if (revealed) {
                        Text(
                            text = card.meaning,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(top = Spacing.lg),
                        )
                        card.example?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = Spacing.md),
                            )
                        }
                        card.exampleTranslation?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                    } else {
                        LinguaButton(
                            text = "Show answer",
                            onClick = { onEvent(FlashcardEvent.Reveal) },
                            modifier = Modifier.padding(top = Spacing.xl),
                        )
                    }
                }
            }
        }

        if (state.isRevealed) GradeActions(state, onEvent)
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}

@Composable
private fun GradeActions(
    state: FlashcardUiState,
    onEvent: (FlashcardEvent) -> Unit,
) {
    val enabled = !state.isSubmittingGrade
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.padding(top = Spacing.md),
    ) {
        GradeButton("Again", ReviewGrade.AGAIN, MaterialTheme.colorScheme.error, onEvent, enabled, Modifier.weight(1f))
        GradeButton("Hard", ReviewGrade.HARD, MaterialTheme.colorScheme.tertiary, onEvent, enabled, Modifier.weight(1f))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.padding(top = Spacing.sm),
    ) {
        GradeButton("Good", ReviewGrade.GOOD, MaterialTheme.colorScheme.primary, onEvent, enabled, Modifier.weight(1f))
        GradeButton("Easy", ReviewGrade.EASY, MaterialTheme.colorScheme.secondary, onEvent, enabled, Modifier.weight(1f))
    }
}

@Composable
private fun GradeButton(
    label: String,
    grade: ReviewGrade,
    color: androidx.compose.ui.graphics.Color,
    onEvent: (FlashcardEvent) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { onEvent(FlashcardEvent.Grade(grade)) },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(label)
    }
}

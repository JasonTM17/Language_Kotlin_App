package com.linguaai.app.ui.screens.flashcard

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.domain.srs.ReviewGrade
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.theme.Spacing

@Composable
fun FlashcardScreen(
    onBack: () -> Unit,
    viewModel: FlashcardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> LoadingIndicator()
        state.finished ->
            EmptyState(
                title = if (state.reviewedCount > 0) "Session complete!" else "Nothing due right now",
                message =
                    if (state.reviewedCount > 0) {
                        "You reviewed ${state.reviewedCount} words. Come back later for the next batch."
                    } else {
                        "All caught up — new words unlock as review times arrive."
                    },
                actionLabel = "Done",
                onAction = onBack,
            )
        else -> FlashcardContent(state, viewModel::onEvent)
    }
}

@Composable
private fun FlashcardContent(
    state: FlashcardUiState,
    onEvent: (FlashcardEvent) -> Unit,
) {
    val card = state.current ?: return
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        Text(
            text = "${state.currentIndex + 1} / ${state.queue.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LinguaCard(modifier = Modifier.padding(top = Spacing.md)) {
            AnimatedContent(targetState = state.isRevealed, label = "flashcard") { revealed ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xl),
                ) {
                    Text(text = card.word, style = MaterialTheme.typography.displaySmall)
                    if (card.reading != null) {
                        Text(
                            text = card.reading,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    if (revealed) {
                        Text(
                            text = card.meaning,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = Spacing.lg),
                        )
                        if (card.example != null) {
                            Text(
                                text = card.example,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = Spacing.md),
                            )
                        }
                        if (card.exampleTranslation != null) {
                            Text(
                                text = card.exampleTranslation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        Spacer(modifier = Modifier.height(Spacing.md))

        if (state.isRevealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GradeButton("Again", ReviewGrade.AGAIN, MaterialTheme.colorScheme.error, onEvent, Modifier.weight(1f))
                GradeButton("Hard", ReviewGrade.HARD, MaterialTheme.colorScheme.tertiary, onEvent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                GradeButton("Good", ReviewGrade.GOOD, MaterialTheme.colorScheme.primary, onEvent, Modifier.weight(1f))
                GradeButton("Easy", ReviewGrade.EASY, MaterialTheme.colorScheme.secondary, onEvent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    grade: ReviewGrade,
    color: androidx.compose.ui.graphics.Color,
    onEvent: (FlashcardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Button(
        onClick = { onEvent(FlashcardEvent.Grade(grade)) },
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = modifier,
    ) {
        Text(label)
    }
}

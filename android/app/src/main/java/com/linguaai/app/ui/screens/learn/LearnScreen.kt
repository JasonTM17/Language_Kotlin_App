package com.linguaai.app.ui.screens.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.theme.Spacing

@Composable
fun LearnScreen(
    onOpenLesson: (Long) -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenGrammar: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onStartQuiz: (Long) -> Unit,
    viewModel: LearnViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Learn",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        )
        OfflineBanner(visible = state.isOffline, modifier = Modifier.padding(horizontal = Spacing.md))

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            ToolChip("Vocabulary", Modifier.weight(1f), onOpenVocabulary)
            ToolChip("Grammar", Modifier.weight(1f), onOpenGrammar)
            ToolChip("Review", Modifier.weight(1f), onOpenFlashcards)
        }
        state.firstQuizId?.let { quizId ->
            LinguaCard(
                onClick = { onStartQuiz(quizId) },
                modifier = Modifier.padding(horizontal = Spacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(Spacing.md),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily quiz", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Test yourself with five quick questions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            items(listOf("N5", "N4", "N3", "A1", "A2", "B1")) { level ->
                FilterChip(
                    selected = state.selectedLevel == level,
                    onClick = { viewModel.selectLevel(if (state.selectedLevel == level) null else level) },
                    label = { Text(level) },
                )
            }
        }

        when {
            state.isLoading && state.lessons.isEmpty() -> LoadingIndicator()
            state.lessons.isEmpty() -> EmptyState(
                title = "No lessons yet",
                message = state.error ?: "Lessons for your level will appear here.",
                actionLabel = "Retry",
                onAction = viewModel::refresh,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.lessons, key = { it.id }) { lesson ->
                    LinguaCard(onClick = { onOpenLesson(lesson.id) }) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${lesson.level} · ${lesson.type.lowercase().replaceFirstChar { it.uppercase() }} · ${lesson.estimatedMinutes} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.xs),
                            ) {
                                Text(
                                    text = lesson.description.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.compose.material3.ElevatedCard(onClick = onClick, modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        )
    }
}

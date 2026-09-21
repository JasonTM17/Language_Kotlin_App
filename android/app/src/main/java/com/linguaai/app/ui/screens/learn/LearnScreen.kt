package com.linguaai.app.ui.screens.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.IconTile
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.theme.Spacing
import com.linguaai.app.ui.util.render

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
    LearnContent(
        state = state,
        onOpenLesson = onOpenLesson,
        onOpenVocabulary = onOpenVocabulary,
        onOpenGrammar = onOpenGrammar,
        onOpenFlashcards = onOpenFlashcards,
        onStartQuiz = onStartQuiz,
        onSelectLevel = viewModel::selectLevel,
        onRefresh = viewModel::refresh,
    )
}

@Composable
private fun LearnContent(
    state: LearnUiState,
    onOpenLesson: (Long) -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenGrammar: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onStartQuiz: (Long) -> Unit,
    onSelectLevel: (String?) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LearnHeader()
        OfflineBanner(visible = state.isOffline, modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm))
        LearnTools(onOpenVocabulary, onOpenGrammar, onOpenFlashcards)
        state.firstQuizId?.let { quizId -> DailyQuizCard(quizId, onStartQuiz) }
        LevelFilters(state.availableLevels, state.selectedLevel, onSelectLevel)
        LessonList(state, onOpenLesson, onOpenVocabulary, onRefresh)
    }
}

@Composable
private fun LearnHeader() {
    Column(modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg)) {
        Text(text = stringResource(R.string.learn_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.learn_build_habit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun LearnTools(
    onOpenVocabulary: () -> Unit,
    onOpenGrammar: () -> Unit,
    onOpenFlashcards: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        ToolCard(stringResource(R.string.vocab_title), Icons.AutoMirrored.Filled.MenuBook, Modifier.weight(1f), onOpenVocabulary)
        ToolCard(stringResource(R.string.grammar_title), Icons.Filled.School, Modifier.weight(1f), onOpenGrammar)
        ToolCard(stringResource(R.string.review_title), Icons.Filled.Refresh, Modifier.weight(1f), onOpenFlashcards)
    }
}

@Composable
private fun DailyQuizCard(
    quizId: Long,
    onStartQuiz: (Long) -> Unit,
) {
    LinguaCard(
        onClick = { onStartQuiz(quizId) },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(horizontal = Spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.md),
        ) {
            IconTile(
                imageVector = Icons.Filled.Quiz,
                contentDescription = null,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.learn_daily_quiz),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.learn_quiz_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun LevelFilters(
    levels: List<String>,
    selectedLevel: String?,
    onSelectLevel: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        items(levels) { level ->
            FilterChip(
                selected = selectedLevel == level,
                onClick = { onSelectLevel(if (selectedLevel == level) null else level) },
                label = { Text(level) },
            )
        }
    }
}

@Composable
private fun ColumnScope.LessonList(
    state: LearnUiState,
    onOpenLesson: (Long) -> Unit,
    onOpenVocabulary: () -> Unit,
    onRefresh: () -> Unit,
) {
    when {
        state.isLoading && state.lessons.isEmpty() -> LoadingIndicator(modifier = Modifier.weight(1f))
        state.lessons.isEmpty() -> {
            val hasError = state.error != null
            EmptyState(
                title = stringResource(if (hasError) R.string.learn_error_title else R.string.learn_ready_title),
                message =
                    state.error?.render()
                        ?: stringResource(R.string.learn_ready_hint),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                actionLabel = stringResource(if (hasError) R.string.common_retry else R.string.learn_explore_vocab),
                onAction = if (hasError) onRefresh else onOpenVocabulary,
                modifier = Modifier.weight(1f),
            )
        }
        else ->
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.lessons, key = { it.id }) { lesson ->
                    LessonCard(lesson, onOpenLesson)
                }
            }
    }
}

@Composable
private fun LessonCard(
    lesson: com.linguaai.app.data.remote.dto.LessonSummaryDto,
    onOpenLesson: (Long) -> Unit,
) {
    LinguaCard(onClick = { onOpenLesson(lesson.id) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.md),
        ) {
            IconTile(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    text =
                        "${lesson.level} · ${lesson.type.lowercase().replaceFirstChar {
                            it.uppercase()
                        }} · ${lesson.estimatedMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = lesson.description.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ToolCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    LinguaCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

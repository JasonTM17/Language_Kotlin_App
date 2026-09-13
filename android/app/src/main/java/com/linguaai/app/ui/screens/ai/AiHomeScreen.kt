package com.linguaai.app.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.data.remote.dto.GeneratedQuizQuestionDto
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.SectionHeader
import com.linguaai.app.ui.theme.Spacing

@Composable
fun AiHomeScreen(
    onOpenConversation: (conversationId: Long?, mode: String) -> Unit,
    viewModel: AiHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Text(
                text = "AI Tutor",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ToolCard("Ask", Icons.Outlined.School, Modifier.weight(1f)) {
                    onOpenConversation(null, "general")
                }
                ToolCard("Practice", Icons.Filled.TheaterComedy, Modifier.weight(1f)) {
                    onOpenConversation(null, "conversation-practice")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ToolCard("Correct", Icons.Filled.Spellcheck, Modifier.weight(1f)) {
                    onOpenConversation(null, "sentence-correction")
                }
                ToolCard("Quiz me", Icons.Filled.Quiz, Modifier.weight(1f)) {
                    viewModel.generateQuiz()
                }
            }
        }

        if (state.isGenerating) {
            item { LoadingIndicator(modifier = Modifier.padding(Spacing.md)) }
        }
        state.quizError?.let { error ->
            item {
                Column(modifier = Modifier.testTag("ai-quiz-error")) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = viewModel::generateQuiz,
                        modifier = Modifier.testTag("ai-quiz-retry"),
                    ) {
                        Text("Retry quiz")
                    }
                }
            }
        }
        if (state.generatedQuiz.isNotEmpty()) {
            item { SectionHeader("Generated quiz") }
            items(state.generatedQuiz) { question ->
                QuizPreviewCard(question)
            }
        }

        item { SectionHeader("Recent conversations") }
        if (!state.isLoading && state.conversations.isEmpty()) {
            item {
                EmptyState(
                    title = "No conversations yet",
                    message = state.conversationError ?: "Start a chat and your history will appear here.",
                    actionLabel = if (state.conversationError == null) null else "Retry",
                    onAction = if (state.conversationError == null) null else viewModel::loadConversations,
                )
            }
        } else {
            items(state.conversations, key = { it.id }) { conversation ->
                LinguaCard(onClick = { onOpenConversation(conversation.id, conversation.mode) }) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(conversation.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            text = conversation.mode,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun QuizPreviewCard(question: GeneratedQuizQuestionDto) {
    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(question.prompt, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Answer: ${question.correctAnswer}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            question.explanation?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

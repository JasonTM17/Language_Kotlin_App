package com.linguaai.app.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.data.remote.dto.GeneratedQuizQuestionDto
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.IconTile
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
    AiHomeContent(
        state = state,
        onOpenConversation = onOpenConversation,
        onGenerateQuiz = viewModel::generateQuiz,
        onRetryConversations = viewModel::loadConversations,
    )
}

@Composable
private fun AiHomeContent(
    state: AiHomeUiState,
    onOpenConversation: (Long?, String) -> Unit,
    onGenerateQuiz: () -> Unit,
    onRetryConversations: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item { TutorHeader() }
        item { AskTutorCard { onOpenConversation(null, "general") } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ToolCard(
                    label = stringResource(R.string.ai_home_practice),
                    icon = Icons.Filled.TheaterComedy,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = { onOpenConversation(null, "conversation-practice") },
                )
                ToolCard(
                    label = stringResource(R.string.ai_home_correct),
                    icon = Icons.Filled.Spellcheck,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { onOpenConversation(null, "sentence-correction") },
                )
            }
        }
        item { QuizGeneratorCard(onGenerateQuiz) }
        if (state.isGenerating) item { QuizLoadingState() }
        state.quizError?.let { error ->
            item { QuizErrorCard(error, onGenerateQuiz) }
        }
        if (state.generatedQuiz.isNotEmpty()) {
            item { SectionHeader("Generated quiz", modifier = Modifier.padding(top = Spacing.md)) }
            items(state.generatedQuiz) { question -> QuizPreviewCard(question) }
        }
        item { SectionHeader(stringResource(R.string.ai_home_recent), modifier = Modifier.padding(top = Spacing.md)) }
        if (state.isLoading && state.conversations.isEmpty()) {
            item { LoadingIndicator(modifier = Modifier.padding(top = Spacing.md)) }
        } else if (state.conversations.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.ai_home_no_conversations),
                    message = state.conversationError ?: stringResource(R.string.ai_home_start_chat),
                    actionLabel = if (state.conversationError == null) null else "Retry",
                    onAction = if (state.conversationError == null) null else onRetryConversations,
                )
            }
        } else {
            items(state.conversations, key = { it.id }) { conversation ->
                ConversationCard(conversation.title, conversation.mode) {
                    onOpenConversation(conversation.id, conversation.mode)
                }
            }
        }
    }
}

@Composable
private fun TutorHeader() {
    Column(modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.sm)) {
        Text(text = stringResource(R.string.ai_home_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.ai_home_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun AskTutorCard(onClick: () -> Unit) {
    LinguaCard(onClick = onClick, containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.md),
        ) {
            IconTile(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ai_home_ask_card),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.ai_home_ask_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ToolCard(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color,
    onClick: () -> Unit,
) {
    LinguaCard(onClick = onClick, containerColor = containerColor, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
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
private fun QuizGeneratorCard(onClick: () -> Unit) {
    LinguaCard(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.md),
        ) {
            IconTile(
                imageVector = Icons.Filled.Quiz,
                contentDescription = null,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_home_quiz_me), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.ai_home_quiz_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuizLoadingState() {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(end = Spacing.sm))
        Text("Preparing your quiz", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun QuizErrorCard(
    error: String,
    onRetry: () -> Unit,
) {
    LinguaCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Column(modifier = Modifier.padding(Spacing.md).testTag("ai-quiz-error")) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRetry, modifier = Modifier.testTag("ai-quiz-retry")) {
                Text("Try again", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun ConversationCard(
    title: String,
    mode: String,
    onClick: () -> Unit,
) {
    LinguaCard(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.md),
        ) {
            IconTile(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mode.replace('-', ' ').replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

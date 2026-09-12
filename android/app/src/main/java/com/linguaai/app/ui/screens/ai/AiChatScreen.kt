package com.linguaai.app.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.data.remote.dto.PracticeScoreDto
import com.linguaai.app.ui.components.ErrorState
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    viewModel: AiChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AiChatContent(
        state = state,
        onInputChanged = viewModel::onInputChanged,
        onSend = viewModel::send,
        onRetry = viewModel::retry,
        onScorePractice = viewModel::scorePractice,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatContent(
    state: AiChatUiState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onScorePractice: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chatTitle(state.mode)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            OfflineBanner(
                visible = state.isOffline,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    state.isLoading -> LoadingIndicator()
                    state.messages.isEmpty() && state.error != null -> ErrorState(
                        message = state.error.orEmpty(),
                        retryLabel = "Retry",
                        retryModifier = Modifier.testTag("ai-chat-retry"),
                        onRetry = onRetry,
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(state.messages) { message ->
                            MessageBubble(message)
                        }
                        if (state.error != null) {
                            item {
                                Column {
                                    Text(
                                        text = state.error.orEmpty(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    TextButton(
                                        onClick = onRetry,
                                        modifier = Modifier.testTag("ai-chat-retry"),
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                        state.practiceScore?.let { score ->
                            item { PracticeScoreCard(score) }
                        }
                    }
                }
            }

            if (state.mode == "conversation-practice" && state.conversationId != null) {
                TextButton(
                    onClick = onScorePractice,
                    enabled = !state.isSending && !state.isScoring,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = Spacing.md)
                        .testTag("ai-practice-score"),
                ) {
                    if (state.isScoring) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(if (state.practiceScore == null) "Finish & score" else "Score again")
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChanged,
                    enabled = !state.isLoading && !state.isSending,
                    placeholder = { Text(chatPlaceholder(state.mode, state.conversationId)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai-chat-input"),
                    maxLines = 3,
                )
                IconButton(
                    onClick = onSend,
                    enabled = !state.isLoading && !state.isSending && state.input.isNotBlank(),
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .testTag("ai-chat-send"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (state.input.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeScoreCard(score: PracticeScoreDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(Spacing.md)
            .testTag("ai-practice-score-result"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("Practice score: ${score.score}/100", style = MaterialTheme.typography.titleMedium)
        Text(
            "Grammar ${score.grammarScore} · Vocabulary ${score.vocabularyScore} · Naturalness ${score.naturalness}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (score.mistakes.isNotEmpty() || score.recommendations.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
        }
        score.mistakes.forEach { Text("Needs work: $it", style = MaterialTheme.typography.bodySmall) }
        score.recommendations.forEach { Text("Next: $it", style = MaterialTheme.typography.bodySmall) }
    }
}

private fun chatTitle(mode: String): String =
    when (mode) {
        "conversation-practice" -> "Conversation practice"
        "sentence-correction" -> "Sentence correction"
        "grammar-explain" -> "Grammar coach"
        "mistakes" -> "Mistakes review"
        else -> "AI Tutor"
    }

private fun chatPlaceholder(
    mode: String,
    conversationId: Long?,
): String =
    when {
        mode == "conversation-practice" && conversationId == null -> "Describe a role-play scenario…"
        mode == "conversation-practice" -> "Reply in the target language…"
        mode == "sentence-correction" -> "Enter a sentence to correct…"
        mode == "mistakes" -> "Ask about a mistake…"
        else -> "Ask anything about your lesson…"
    }

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "USER"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    ),
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            if (message.isPending) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(text = message.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

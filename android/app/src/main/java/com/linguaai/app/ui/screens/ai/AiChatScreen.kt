package com.linguaai.app.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.data.remote.dto.AiSourceDto
import com.linguaai.app.data.remote.dto.PracticeScoreDto
import com.linguaai.app.ui.components.ErrorState
import com.linguaai.app.ui.components.LinguaMascot
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.theme.BrandGradients
import com.linguaai.app.ui.theme.Spacing

/**
 * The role-play mode. It is the only mode with a scoring action, and the only
 * one whose placeholder depends on whether a scenario has been started.
 */
private const val CONVERSATION_PRACTICE_MODE = "conversation-practice"

/** Shown on an empty transcript so the tutor never starts from a dead screen. */
private val SUGGESTED_PROMPTS =
    listOf(
        R.string.chat_prompt_grammar,
        R.string.chat_prompt_questions,
        R.string.chat_prompt_food,
        R.string.chat_prompt_travel,
    )

/** Sources display per assistant bubble; more than this hurts readability. */
private const val MAX_VISIBLE_SOURCES = 4

private const val SOURCE_TITLE_MAX_LENGTH = 32

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
                title = { Text(stringResource(chatTitle(state.mode))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
        ) {
            OfflineBanner(
                visible = state.isOffline,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            ChatTranscript(
                state = state,
                listState = listState,
                onRetry = onRetry,
                onUsePrompt = onInputChanged,
            )
            if (state.mode == CONVERSATION_PRACTICE_MODE && state.conversationId != null) {
                PracticeScoreAction(state = state, onScorePractice = onScorePractice)
            }
            ChatComposer(state = state, onInputChanged = onInputChanged, onSend = onSend)
        }
    }
}

/** The scrolling transcript, and the loading, empty and error states it can be in. */
@Composable
private fun ColumnScope.ChatTranscript(
    state: AiChatUiState,
    listState: LazyListState,
    onRetry: () -> Unit,
    onUsePrompt: (String) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth(),
    ) {
        when {
            state.isLoading -> LoadingIndicator()
            state.messages.isEmpty() && state.error != null ->
                ErrorState(
                    message = state.error.orEmpty(),
                    retryLabel = "Retry",
                    retryModifier = Modifier.testTag("ai-chat-retry"),
                    onRetry = onRetry,
                )
            // A scored role-play must stay visible even with an empty
            // transcript: the score card lives in the list below.
            state.messages.isEmpty() && state.practiceScore == null -> EmptyTranscript(onUsePrompt = onUsePrompt)
            else ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.md),
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
}

/** Brand lockup and starter prompts shown before the first exchange. */
@Composable
private fun EmptyTranscript(onUsePrompt: (String) -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        LinguaMascot(
            contentDescription = stringResource(R.string.mascot_content_description),
            mascotSize = 104.dp,
        )
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = stringResource(R.string.chat_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Spacer(modifier = Modifier.weight(1f))
        SUGGESTED_PROMPTS.forEach { promptRes ->
            val prompt = stringResource(promptRes)
            SuggestionChip(
                onClick = { onUsePrompt(prompt) },
                label = { Text(prompt) },
                modifier = Modifier.padding(vertical = Spacing.xs),
            )
        }
        Spacer(modifier = Modifier.heightIn(min = Spacing.lg))
    }
}

/** The role-play scoring action, shown only once a practice conversation exists. */
@Composable
private fun ColumnScope.PracticeScoreAction(
    state: AiChatUiState,
    onScorePractice: () -> Unit,
) {
    TextButton(
        onClick = onScorePractice,
        enabled = !state.isSending && !state.isScoring,
        modifier =
            Modifier
                .align(Alignment.End)
                .padding(horizontal = Spacing.md)
                .testTag("ai-practice-score"),
    ) {
        if (state.isScoring) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                if (state.practiceScore == null) {
                    stringResource(R.string.chat_score_action)
                } else {
                    stringResource(R.string.chat_score_again)
                },
            )
        }
    }
}

/** The input row: the pill-shaped text field and its circular send action. */
@Composable
private fun ChatComposer(
    state: AiChatUiState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChanged,
            enabled = !state.isLoading && !state.isSending,
            placeholder = { Text(chatPlaceholder(state.mode, state.conversationId)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions =
                KeyboardActions(
                    onSend = {
                        if (!state.isLoading && !state.isSending && state.input.isNotBlank()) {
                            onSend()
                        }
                    },
                ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier =
                Modifier
                    .weight(1f)
                    .testTag("ai-chat-input"),
            maxLines = 3,
        )
        FilledIconButton(
            onClick = onSend,
            enabled = !state.isLoading && !state.isSending && state.input.isNotBlank(),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            modifier =
                Modifier
                    .padding(start = Spacing.sm, bottom = Spacing.xs)
                    .size(48.dp)
                    .testTag("ai-chat-send"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.common_send_message),
            )
        }
    }
}

@Composable
private fun PracticeScoreCard(score: PracticeScoreDto) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("ai-practice-score-result"),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                stringResource(R.string.chat_score_title, score.score),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(
                    R.string.chat_score_breakdown,
                    score.grammarScore,
                    score.vocabularyScore,
                    score.naturalness,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (score.mistakes.isNotEmpty() || score.recommendations.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
            }
            score.mistakes.forEach {
                Text(
                    stringResource(R.string.chat_needs_work, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            score.recommendations.forEach {
                Text(
                    stringResource(R.string.chat_next, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private fun chatTitle(mode: String): Int =
    when (mode) {
        CONVERSATION_PRACTICE_MODE -> R.string.chat_title_practice
        "sentence-correction" -> R.string.chat_title_correction
        "grammar-explain" -> R.string.chat_title_grammar
        "mistakes" -> R.string.chat_title_mistakes
        else -> R.string.chat_title_tutor
    }

@Composable
private fun chatPlaceholder(
    mode: String,
    conversationId: Long?,
): String =
    when {
        mode == CONVERSATION_PRACTICE_MODE && conversationId == null ->
            stringResource(R.string.chat_hint_practice_setup)
        mode == CONVERSATION_PRACTICE_MODE ->
            stringResource(R.string.chat_hint_practice_reply)
        mode == "sentence-correction" -> stringResource(R.string.chat_hint_correction)
        mode == "mistakes" -> stringResource(R.string.chat_hint_mistakes)
        else -> stringResource(R.string.chat_hint_general)
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "USER"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        val bubbleShape =
            RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp,
            )
        Box(
            modifier =
                Modifier
                    .widthIn(max = 320.dp)
                    .shadow(elevation = 1.dp, shape = bubbleShape, clip = false)
                    .clip(bubbleShape)
                    .background(
                        if (isUser) {
                            BrandGradients.AccentPill
                        } else {
                            SolidColor(MaterialTheme.colorScheme.surfaceVariant)
                        },
                    ).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Column {
                if (message.isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (isUser) BrandGradients.OnHero else LocalContentColor.current,
                    )
                } else {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) BrandGradients.OnHero else Color.Unspecified,
                    )
                }
                if (!isUser && message.sources.isNotEmpty()) {
                    SourceChips(message.sources)
                }
            }
        }
    }
}

/** Retrieved course-corpus citations rendered under an assistant reply. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceChips(sources: List<AiSourceDto>) {
    Text(
        text = stringResource(R.string.chat_grounded),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
    FlowRow(
        modifier = Modifier.padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        sources.take(MAX_VISIBLE_SOURCES).forEach { source ->
            SourceChip(source)
        }
    }
}

@Composable
private fun SourceChip(source: AiSourceDto) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
        ) {
            Icon(
                imageVector = sourceIcon(source.sourceType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = source.title.take(SOURCE_TITLE_MAX_LENGTH),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

private fun sourceIcon(sourceType: String): ImageVector =
    when (sourceType) {
        "GRAMMAR" -> Icons.Filled.School
        "LESSON" -> Icons.Filled.Description
        else -> Icons.AutoMirrored.Filled.MenuBook
    }

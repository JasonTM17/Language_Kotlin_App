package com.linguaai.app.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.linguaai.app.domain.model.AppError
import com.linguaai.app.ui.components.ErrorState
import com.linguaai.app.ui.components.LinguaMascot
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.theme.BrandGradients
import com.linguaai.app.ui.theme.Spacing
import com.linguaai.app.ui.util.TutorRichText
import com.linguaai.app.ui.util.asUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/** RoundedCornerShape takes a percentage; 50 gives a pill. */
private const val PILL_PERCENT = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    onOpenSource: (AiSourceDto) -> Unit,
    viewModel: AiChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AiChatContent(
        state = state,
        onInputChanged = viewModel::onInputChanged,
        onSend = viewModel::send,
        onRetry = viewModel::retry,
        onScorePractice = viewModel::scorePractice,
        onStop = viewModel::stop,
        onOpenSource = onOpenSource,
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
    onStop: () -> Unit,
    onOpenSource: (AiSourceDto) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Only follow the transcript when the learner was already at the bottom;
    // otherwise a reply, a stop or a retry yanks them away from what they were
    // re-reading.
    LaunchedEffect(state.messages.size) {
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index
        val atBottom = lastVisible == null || lastVisible >= info.totalItemsCount - 2
        if (info.totalItemsCount > 0 && atBottom) {
            listState.animateScrollToItem(info.totalItemsCount - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ChatHeader(mode = state.mode)
                },
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
                message = stringResource(R.string.state_offline_banner),
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            if (state.offlineSendNotice) {
                NoticeBanner(
                    text = stringResource(R.string.chat_offline_send),
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ChatTranscript(
                    state = state,
                    listState = listState,
                    onRetry = onRetry,
                    onUsePrompt = onInputChanged,
                    onStop = onStop,
                    onOpenSource = onOpenSource,
                )
                ScrollToBottomButton(
                    listState = listState,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = Spacing.md, bottom = Spacing.sm),
                )
            }
            if (state.mode == CONVERSATION_PRACTICE_MODE && state.conversationId != null) {
                PracticeScoreAction(state = state, onScorePractice = onScorePractice)
            }
            ChatComposer(state = state, onInputChanged = onInputChanged, onSend = onSend)
        }
    }
}

@Composable
private fun ChatHeader(mode: String) {
    Column {
        Text(
            text = stringResource(chatModeTitleRes(mode)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.chat_tutor_available),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The scrolling transcript, and the loading, empty and error states it can be in. */
@Composable
private fun ChatTranscript(
    state: AiChatUiState,
    listState: LazyListState,
    onRetry: () -> Unit,
    onUsePrompt: (String) -> Unit,
    onStop: () -> Unit,
    onOpenSource: (AiSourceDto) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        when {
            state.isLoading -> LoadingIndicator()
            state.messages.isEmpty() && state.error != null ->
                ErrorState(
                    message = state.error.asUserMessage(),
                    retryLabel = stringResource(R.string.common_retry),
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
                        MessageBubble(
                            message = message,
                            onStop = onStop,
                            onOpenSource = onOpenSource,
                        )
                    }
                    state.error?.let { error ->
                        item {
                            ErrorNotice(
                                error = error,
                                onRetry = onRetry,
                            )
                        }
                    }
                    state.practiceScore?.let { score ->
                        item { PracticeScoreCard(score) }
                    }
                }
        }
    }
}

@Composable
private fun ErrorNotice(
    error: AppError,
    onRetry: () -> Unit,
) {
    val retryAfter = (error as? AppError.RateLimited)?.retryAfterSeconds
    var secondsLeft by remember(error) { mutableStateOf(retryAfter ?: 0L) }
    LaunchedEffect(secondsLeft) {
        while (secondsLeft > 0L) {
            delay(1000L)
            secondsLeft -= 1L
        }
    }

    Column {
        if (error is AppError.RateLimited) {
            NoticeBanner(
                text =
                    if (secondsLeft > 0L) {
                        stringResource(R.string.err_rate_limited) + " " +
                            stringResource(R.string.chat_retry_in, secondsLeft)
                    } else {
                        stringResource(R.string.err_rate_limited)
                    },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            Text(
                text = error.asUserMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(
            onClick = onRetry,
            modifier = Modifier.testTag("ai-chat-retry"),
        ) {
            Text(
                if (error is AppError.RateLimited && secondsLeft > 0L) {
                    stringResource(R.string.chat_retry_now)
                } else {
                    stringResource(R.string.common_retry)
                },
            )
        }
    }
}

@Composable
private fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

/** Appears only once the learner has scrolled away from the newest message. */
@Composable
private fun ScrollToBottomButton(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    if (!listState.canScrollForward) return
    FilledIconButton(
        onClick = { scope.launch { listState.scrollToItem(Int.MAX_VALUE) } },
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        modifier =
            modifier
                .size(40.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                    shape = MaterialTheme.shapes.small,
                ).testTag("ai-chat-scroll-bottom"),
    ) {
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = stringResource(R.string.chat_scroll_to_bottom),
        )
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

internal fun chatModeTitleRes(mode: String): Int =
    when (mode) {
        CONVERSATION_PRACTICE_MODE -> R.string.chat_title_practice
        "sentence-correction" -> R.string.chat_title_correction
        "grammar-explain" -> R.string.chat_title_grammar
        "lesson-context" -> R.string.chat_title_lesson
        "mistakes", "mistakes-review" -> R.string.chat_title_mistakes
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

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onStop: () -> Unit,
    onOpenSource: (AiSourceDto) -> Unit,
) {
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
                    .clip(bubbleShape)
                    .then(
                        if (isUser) {
                            Modifier.background(BrandGradients.AccentPill)
                        } else {
                            // The Stitch spec replaces the grey fill with an ivory
                            // card and a hairline so long tutor answers read as text,
                            // not as a tinted block.
                            Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                                    shape = bubbleShape,
                                )
                        },
                    ).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Column {
                if (message.isPending) {
                    PendingReply(onStop = onStop)
                } else if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandGradients.OnHero,
                    )
                } else {
                    TutorRichText(
                        content = message.content,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!isUser && message.sources.isNotEmpty()) {
                    SourceChips(
                        sources = message.sources,
                        onOpenSource = onOpenSource,
                    )
                }
            }
        }
    }
}

/** The in-flight state: the tutor is visibly working, and can be interrupted. */
@Composable
private fun PendingReply(onStop: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.chat_generating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        TextSurfaceButton(
            label = stringResource(R.string.chat_stop),
            icon = Icons.Filled.Stop,
            onClick = onStop,
            modifier = Modifier.testTag("ai-chat-stop"),
        )
    }
}

@Composable
private fun TextSurfaceButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(PILL_PERCENT),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Retrieved course-corpus citations rendered under an assistant reply. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceChips(
    sources: List<AiSourceDto>,
    onOpenSource: (AiSourceDto) -> Unit,
) {
    Text(
        text = stringResource(R.string.chat_grounded),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
    FlowRow(
        modifier = Modifier.padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        sources.take(MAX_VISIBLE_SOURCES).forEach { source ->
            SourceChip(source = source, onOpenSource = onOpenSource)
        }
    }
}

/**
 * A citation the learner can act on. Vocabulary hits stay inert because the
 * vocabulary destination carries no id, so there is nothing to open.
 */
@Composable
private fun SourceChip(
    source: AiSourceDto,
    onOpenSource: (AiSourceDto) -> Unit,
) {
    val clickable = source.sourceType == "LESSON" || source.sourceType == "GRAMMAR"
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier =
            Modifier
                .then(
                    if (clickable) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                                shape = MaterialTheme.shapes.small,
                            ).clickable { onOpenSource(source) }
                            .testTag("ai-chat-source")
                    } else {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                                shape = MaterialTheme.shapes.small,
                            )
                    },
                ),
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
            source.level?.let { level ->
                Spacer(modifier = Modifier.width(Spacing.xs))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(PILL_PERCENT),
                ) {
                    Text(
                        text = level,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private fun sourceIcon(sourceType: String): ImageVector =
    when (sourceType) {
        "GRAMMAR" -> Icons.Filled.School
        "LESSON" -> Icons.Filled.Description
        else -> Icons.AutoMirrored.Filled.MenuBook
    }

package com.linguaai.app.ui.screens.listenandtype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.domain.model.ListenAndTypeRound
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.ErrorState
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.theme.Spacing
import com.linguaai.app.ui.util.rememberLinguaTts

@Composable
fun ListenAndTypeScreen(
    onBack: () -> Unit,
    onOpenVocabulary: () -> Unit,
    viewModel: ListenAndTypeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tts = rememberLinguaTts(state.languageCode)
    val latestTts by rememberUpdatedState(tts)
    LaunchedEffect(state.round?.currentWord?.id) {
        latestTts.stop()
    }

    ListenAndTypeContent(
        state = state,
        onBack = onBack,
        onOpenVocabulary = onOpenVocabulary,
        onRetryLoad = viewModel::loadRound,
        onSubmitAnswer = viewModel::submitAnswer,
        onAdvance = viewModel::advance,
        onRetryRound = viewModel::retryRound,
        onPlayWord = { word ->
            tts.speak(
                text = word.word,
                languageCode = state.languageCode,
                fallbackText = listenAndTypeFallbackText(word, state.languageCode),
            )
        },
    )
}

internal fun listenAndTypeFallbackText(
    word: VocabularyCard,
    languageCode: String?,
): String {
    val isJapanese =
        languageCode.equals("ja", ignoreCase = true) ||
            languageCode.equals("jpn", ignoreCase = true)
    return word.reading?.takeIf { isJapanese && it.isNotBlank() } ?: word.word
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ListenAndTypeContent(
    state: ListenAndTypeUiState,
    onBack: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onRetryLoad: () -> Unit,
    onSubmitAnswer: (String) -> Unit,
    onAdvance: () -> Unit,
    onRetryRound: () -> Unit,
    onPlayWord: (VocabularyCard) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.listen_type_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.padding(padding))
            state.loadError == ListenAndTypeLoadError.LANGUAGE_REQUIRED -> {
                EmptyState(
                    title = stringResource(R.string.listen_type_no_language_title),
                    message = stringResource(R.string.msg_need_language_review),
                    modifier = Modifier.padding(padding).padding(horizontal = Spacing.md),
                    actionLabel = stringResource(R.string.common_back),
                    onAction = onBack,
                )
            }
            state.loadError == ListenAndTypeLoadError.LANGUAGE_CODE_UNAVAILABLE -> {
                ErrorState(
                    message = stringResource(R.string.listen_type_language_unavailable),
                    modifier = Modifier.padding(padding),
                    retryLabel = stringResource(R.string.common_retry),
                    onRetry = onRetryLoad,
                )
            }
            state.loadError == ListenAndTypeLoadError.LOAD_FAILED || state.round == null -> {
                ErrorState(
                    message = stringResource(R.string.listen_type_load_failed),
                    modifier = Modifier.padding(padding),
                    retryLabel = stringResource(R.string.common_retry),
                    onRetry = onRetryLoad,
                )
            }
            state.round.isEmpty -> {
                EmptyGameState(
                    onBack = onBack,
                    onOpenVocabulary = onOpenVocabulary,
                    modifier = Modifier.padding(padding),
                )
            }
            state.round.isComplete -> {
                CompleteGameState(
                    correctCount = state.round.correctCount,
                    total = state.round.words.size,
                    onBack = onBack,
                    onRetry = onRetryRound,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                val round = state.round
                val word = round.currentWord ?: return@Scaffold
                GameQuestion(
                    round = round,
                    word = word,
                    onSubmitAnswer = onSubmitAnswer,
                    onAdvance = onAdvance,
                    onPlayWord = { onPlayWord(word) },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun EmptyGameState(
    onBack: () -> Unit,
    onOpenVocabulary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            title = stringResource(R.string.listen_type_empty_title),
            message = stringResource(R.string.listen_type_empty_hint),
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            actionLabel = stringResource(R.string.listen_type_open_vocabulary),
            onAction = onOpenVocabulary,
        )
        OutlinedButton(
            onClick = onBack,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(top = Spacing.sm)
                    .padding(horizontal = Spacing.md),
        ) {
            Text(stringResource(R.string.common_back))
        }
    }
}

@Composable
private fun CompleteGameState(
    correctCount: Int,
    total: Int,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            title = stringResource(R.string.listen_type_complete_title),
            message = stringResource(R.string.listen_type_score, correctCount, total),
            actionLabel = stringResource(R.string.listen_type_retry),
            onAction = onRetry,
        )
        OutlinedButton(
            onClick = onBack,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(top = Spacing.sm)
                    .padding(horizontal = Spacing.md),
        ) {
            Text(stringResource(R.string.listen_type_done))
        }
    }
}

@Composable
private fun GameQuestion(
    round: ListenAndTypeRound,
    word: VocabularyCard,
    onSubmitAnswer: (String) -> Unit,
    onAdvance: () -> Unit,
    onPlayWord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var answer by remember(word.id) { mutableStateOf("") }
    val isAnswered = round.hasAnswered
    val feedbackMessage =
        when (round.answeredCorrectly) {
            true -> stringResource(R.string.listen_type_correct)
            false -> stringResource(R.string.listen_type_incorrect)
            null -> null
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        RoundProgress(round)
        Text(
            text = stringResource(R.string.listen_type_prompt),
            style = MaterialTheme.typography.titleMedium,
        )
        AudioPromptCard(onPlayWord)

        if (!isAnswered) {
            AnswerEntry(answer, onAnswerChange = { answer = it }, onSubmitAnswer)
        } else {
            AnswerFeedback(round, word, feedbackMessage.orEmpty(), onAdvance)
        }
    }
}

@Composable
private fun RoundProgress(round: ListenAndTypeRound) {
    val label = stringResource(R.string.listen_type_progress, round.currentIndex + 1, round.words.size)
    Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LinearProgressIndicator(
        progress = { (round.currentIndex + 1f) / round.words.size },
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            current = (round.currentIndex + 1).toFloat(),
                            range = 0f..round.words.size.toFloat(),
                            steps = (round.words.size - 1).coerceAtLeast(0),
                        )
                },
    )
}

@Composable
private fun AudioPromptCard(onPlayWord: () -> Unit) {
    LinguaCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.listen_type_hidden_word),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            androidx.compose.material3.FilledTonalButton(
                onClick = onPlayWord,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = Spacing.xs),
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                Text(
                    text = stringResource(R.string.listen_type_play_audio),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun AnswerEntry(
    answer: String,
    onAnswerChange: (String) -> Unit,
    onSubmitAnswer: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        if (answer.isNotBlank()) {
            onSubmitAnswer(answer)
            keyboard?.hide()
        }
    }
    TextField(
        value = answer,
        onValueChange = onAnswerChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.listen_type_answer_label)) },
        placeholder = { Text(stringResource(R.string.listen_type_answer_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
    LinguaButton(
        text = stringResource(R.string.listen_type_check),
        onClick = { submit() },
        enabled = answer.isNotBlank(),
    )
}

@Composable
private fun AnswerFeedback(
    round: ListenAndTypeRound,
    word: VocabularyCard,
    message: String,
    onAdvance: () -> Unit,
) {
    val isCorrect = round.answeredCorrectly == true
    LinguaCard(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        containerColor = if (isCorrect) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCorrect) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.listen_type_correct_word, word.word),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(word.meaning, style = MaterialTheme.typography.bodyMedium)
        }
    }
    LinguaButton(text = stringResource(R.string.listen_type_next), onClick = onAdvance)
}

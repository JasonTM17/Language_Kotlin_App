package com.linguaai.app.ui.screens.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LinguaOutlinedButton
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.theme.Spacing

/** A result reads as strong work once this fraction of answers is correct. */
private const val STRONG_WORK_FRACTION = 0.8f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    onBack: () -> Unit,
    onAskAiAboutMistakes: (Long) -> Unit,
    viewModel: QuizViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.quiz?.title ?: stringResource(R.string.quiz_title_fallback)) },
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
        when {
            state.result != null ->
                QuizResultContent(
                    score = state.result!!.score,
                    total = state.result!!.total,
                    weakTopics = state.result!!.weakTopics,
                    onAskAi = { onAskAiAboutMistakes(state.result!!.quizId) },
                    onDone = onBack,
                    modifier = Modifier.padding(padding),
                )
            state.isLoading -> LoadingIndicator(modifier = Modifier.padding(padding))
            state.quiz == null ->
                EmptyState(
                    title = stringResource(R.string.quiz_unavailable),
                    message = state.error ?: stringResource(R.string.quiz_not_loaded),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = viewModel::load,
                    modifier = Modifier.padding(padding),
                )
            else ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.md, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.quiz_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.quiz!!.questions.forEachIndexed { index, question ->
                        LinguaCard(modifier = Modifier.padding(top = Spacing.sm)) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Text(
                                    text = stringResource(R.string.quiz_question_number, index + 1),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = question.prompt,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = Spacing.xs),
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    modifier = Modifier.padding(top = Spacing.sm),
                                ) {
                                    question.options.forEach { option ->
                                        FilterChip(
                                            selected = state.answers[question.id] == option,
                                            onClick = {
                                                viewModel.onEvent(QuizEvent.AnswerSelected(question.id, option))
                                            },
                                            label = { Text(option, maxLines = 2) },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .semantics {
                                                        contentDescription = "Option: $option"
                                                    },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    state.error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    LinguaButton(
                        text = stringResource(R.string.quiz_submit),
                        onClick = { viewModel.onEvent(QuizEvent.Submit) },
                        enabled = state.allAnswered,
                        isLoading = state.isSubmitting,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg))
                }
        }
    }
}

@Composable
private fun QuizResultContent(
    score: Int,
    total: Int,
    weakTopics: List<String>,
    onAskAi: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (total == 0) 0f else (score.toFloat() / total).coerceIn(0f, 1f)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
                strokeWidth = 10.dp,
            )
            Text(
                text = "$score/$total",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text =
                stringResource(
                    if (fraction >= STRONG_WORK_FRACTION) R.string.quiz_strong_work else R.string.quiz_keep_practicing,
                ),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = stringResource(R.string.quiz_score_out_of, score, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (weakTopics.isNotEmpty()) {
            LinguaCard(modifier = Modifier.padding(top = Spacing.lg)) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(stringResource(R.string.quiz_worth_revisiting), style = MaterialTheme.typography.titleSmall)
                    weakTopics.forEach { topic ->
                        Text(
                            text = topic,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                }
            }
        }
        LinguaButton(
            text = stringResource(R.string.quiz_ask_mistakes),
            onClick = onAskAi,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        LinguaOutlinedButton(
            text = stringResource(R.string.common_done),
            onClick = onDone,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

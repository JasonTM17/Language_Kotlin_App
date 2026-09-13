package com.linguaai.app.ui.screens.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.theme.Spacing

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
                title = { Text(state.quiz?.title ?: "Quiz") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            state.isLoading -> LoadingIndicator()
            state.quiz == null ->
                EmptyState(
                    title = "Quiz unavailable",
                    message = state.error ?: "This quiz could not be loaded.",
                    actionLabel = "Retry",
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
                            .padding(horizontal = Spacing.md),
                ) {
                    state.quiz!!.questions.forEach { question ->
                        LinguaCard(modifier = Modifier.padding(top = Spacing.md)) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Text(question.prompt, style = MaterialTheme.typography.titleMedium)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.sm),
                                ) {
                                    question.options.forEach { option ->
                                        FilterChip(
                                            selected = state.answers[question.id] == option,
                                            onClick = {
                                                viewModel.onEvent(QuizEvent.AnswerSelected(question.id, option))
                                            },
                                            label = { Text(option, maxLines = 2) },
                                            modifier =
                                                Modifier.semantics {
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
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    LinguaButton(
                        text = "Submit answers",
                        onClick = { viewModel.onEvent(QuizEvent.Submit) },
                        enabled = state.allAnswered,
                        isLoading = state.isSubmitting,
                        modifier = Modifier.padding(top = Spacing.lg),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xl))
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
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Score: $score/$total",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = Spacing.xl),
        )
        if (weakTopics.isNotEmpty()) {
            LinguaCard(modifier = Modifier.padding(top = Spacing.lg)) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text("Weak topics", style = MaterialTheme.typography.titleSmall)
                    weakTopics.forEach { topic ->
                        Text(
                            text = "• $topic",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }
        LinguaButton(
            text = "Ask AI to explain my mistakes",
            onClick = onAskAi,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        LinguaButton(
            text = "Done",
            onClick = onDone,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

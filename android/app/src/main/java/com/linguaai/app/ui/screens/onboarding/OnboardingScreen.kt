package com.linguaai.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.ErrorState
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.theme.Spacing

@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingEvent.Completed -> onCompleted()
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
    ) {
        val stepIndex = OnboardingStep.entries.indexOf(state.step)
        val total = OnboardingStep.entries.size
        LinearProgressIndicator(
            progress = { (stepIndex + 1) / total.toFloat() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Onboarding step ${stepIndex + 1} of $total" },
        )
        Text(
            text = "Step ${stepIndex + 1} of $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = state.step.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md),
        )

        when {
            state.isLoading -> LoadingIndicator()
            state.error != null ->
                ErrorState(
                    message = state.error.orEmpty(),
                    retryLabel = "Retry",
                    onRetry = viewModel::loadLanguages,
                )
            else ->
                when (state.step) {
                    OnboardingStep.LANGUAGE -> LanguageStep(state, viewModel::selectLanguage)
                    OnboardingStep.LEVEL ->
                        ChoiceStep(
                            options = state.currentLevels,
                            selected = state.selectedLevel,
                            onSelect = viewModel::selectLevel,
                        )
                    OnboardingStep.GOAL ->
                        ChoiceStep(
                            options = OnboardingCatalog.goals,
                            selected = state.selectedGoal,
                            onSelect = viewModel::selectGoal,
                        )
                    OnboardingStep.DAILY ->
                        DailyGoalStep(
                            selected = state.selectedDailyGoal,
                            onSelect = viewModel::selectDailyGoal,
                        )
                }
        }

        Row(modifier = Modifier.padding(top = Spacing.md)) {
            if (state.step != OnboardingStep.LANGUAGE) {
                TextButton(onClick = viewModel::back) {
                    Text("Back")
                }
            }
            LinguaButton(
                text = if (state.step == OnboardingStep.DAILY) "Start learning" else "Continue",
                onClick = viewModel::next,
                enabled = state.canContinue,
                isLoading = state.isSubmitting,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = Spacing.sm),
            )
        }
    }
}

@Composable
private fun LanguageStep(
    state: OnboardingUiState,
    onSelect: (Long) -> Unit,
) {
    if (state.languages.isEmpty()) {
        EmptyState(title = "No languages yet", message = "The server has no language catalogue.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        state.languages.forEach { language ->
            SelectableRow(
                title = language.name,
                subtitle = language.levels.joinToString(" · "),
                selected = state.selectedLanguageId == language.id,
                onClick = { onSelect(language.id) },
            )
        }
    }
}

@Composable
private fun ChoiceStep(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(options) { option ->
            SelectableRow(
                title = option,
                subtitle = null,
                selected = selected == option,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun DailyGoalStep(
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = "How many minutes per day do you want to study?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(vertical = Spacing.sm),
        ) {
            items(OnboardingCatalog.dailyGoals) { minutes ->
                FilterChip(
                    selected = selected == minutes,
                    onClick = { onSelect(minutes) },
                    label = { Text("$minutes min") },
                )
            }
        }
    }
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    androidx.compose.material3.Card(
        onClick = onClick,
        colors =
            androidx.compose.material3.CardDefaults
                .cardColors(containerColor = container),
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = if (selected) "$title selected" else title },
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

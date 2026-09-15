package com.linguaai.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.ErrorState
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.theme.Spacing

private const val DAILY_GOALS_PER_ROW = 3

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

    OnboardingContent(
        state = state,
        onAction = { action ->
            when (action) {
                OnboardingAction.Retry -> viewModel.loadLanguages()
                is OnboardingAction.LanguageSelected -> viewModel.selectLanguage(action.id)
                is OnboardingAction.LevelSelected -> viewModel.selectLevel(action.value)
                is OnboardingAction.GoalSelected -> viewModel.selectGoal(action.value)
                is OnboardingAction.DailyGoalSelected -> viewModel.selectDailyGoal(action.minutes)
                OnboardingAction.Back -> viewModel.back()
                OnboardingAction.Next -> viewModel.next()
            }
        },
    )
}

internal sealed interface OnboardingAction {
    data object Retry : OnboardingAction

    data class LanguageSelected(
        val id: Long,
    ) : OnboardingAction

    data class LevelSelected(
        val value: String,
    ) : OnboardingAction

    data class GoalSelected(
        val value: String,
    ) : OnboardingAction

    data class DailyGoalSelected(
        val minutes: Int,
    ) : OnboardingAction

    data object Back : OnboardingAction

    data object Next : OnboardingAction
}

@Composable
internal fun OnboardingContent(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
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
        Row(
            modifier = Modifier.padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Step ${stepIndex + 1} of $total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Set up your learning path",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = state.step.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md),
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> LoadingIndicator()
                state.error != null ->
                    ErrorState(
                        message = state.error.orEmpty(),
                        retryLabel = "Retry",
                        onRetry = { onAction(OnboardingAction.Retry) },
                    )
                else ->
                    when (state.step) {
                        OnboardingStep.LANGUAGE ->
                            LanguageStep(state) { id ->
                                onAction(OnboardingAction.LanguageSelected(id))
                            }
                        OnboardingStep.LEVEL ->
                            ChoiceStep(
                                options = state.currentLevels,
                                selected = state.selectedLevel,
                                onSelect = { value -> onAction(OnboardingAction.LevelSelected(value)) },
                            )
                        OnboardingStep.GOAL ->
                            ChoiceStep(
                                options = OnboardingCatalog.goals,
                                selected = state.selectedGoal,
                                onSelect = { value -> onAction(OnboardingAction.GoalSelected(value)) },
                            )
                        OnboardingStep.DAILY ->
                            DailyGoalStep(
                                selected = state.selectedDailyGoal,
                                onSelect = { minutes -> onAction(OnboardingAction.DailyGoalSelected(minutes)) },
                            )
                    }
            }
        }

        Row(
            modifier = Modifier.padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step != OnboardingStep.LANGUAGE) {
                TextButton(onClick = { onAction(OnboardingAction.Back) }) {
                    Text("Back")
                }
            }
            LinguaButton(
                text = if (state.step == OnboardingStep.DAILY) "Start learning" else "Continue",
                onClick = { onAction(OnboardingAction.Next) },
                enabled = state.canContinue,
                isLoading = state.isSubmitting,
                modifier = Modifier.weight(1f).padding(start = Spacing.sm),
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
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(state.languages, key = { it.id }) { language ->
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OnboardingCatalog.dailyGoals.chunked(DAILY_GOALS_PER_ROW).forEach { rowGoals ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    rowGoals.forEach { minutes ->
                        FilterChip(
                            selected = selected == minutes,
                            onClick = { onSelect(minutes) },
                            label = { Text("$minutes min") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(DAILY_GOALS_PER_ROW - rowGoals.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
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
            MaterialTheme.colorScheme.surface
        }
    LinguaCard(
        onClick = onClick,
        containerColor = container,
        modifier = Modifier.semantics { contentDescription = if (selected) "$title selected" else title },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

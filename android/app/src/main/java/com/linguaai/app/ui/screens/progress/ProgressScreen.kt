package com.linguaai.app.ui.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.data.remote.dto.ActivityDayDto
import com.linguaai.app.data.remote.dto.ProgressSummaryDto
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.ErrorState
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.components.SectionHeader
import com.linguaai.app.ui.theme.Spacing

/** The server reports quiz accuracy as a 0..1 ratio; the UI shows a percentage. */
private const val PERCENT_SCALE = 100

/**
 * Activity chart geometry: the tallest bar, and the height a day with any
 * activity at all gets so a one-minute day is still visible.
 */
private const val CHART_MAX_BAR_HEIGHT_DP = 48
private const val CHART_MIN_BAR_HEIGHT_DP = 4f

@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    ProgressContent(
        state = state,
        isOnline = isOnline,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

@Composable
private fun ProgressContent(
    state: ProgressUiState,
    isOnline: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = state.summary

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
    ) {
        OfflineBanner(
            visible = !isOnline || state.isStale,
            message =
                if (isOnline) {
                    stringResource(R.string.state_stale_banner)
                } else {
                    stringResource(R.string.state_offline_banner)
                },
            modifier = Modifier.padding(top = Spacing.sm),
        )

        SectionHeader(title = stringResource(R.string.progress_title), modifier = Modifier.padding(top = Spacing.md))

        when {
            state.isLoading && summary == null -> LoadingIndicator(modifier = Modifier.padding(top = Spacing.xxl))

            summary == null && state.error != null ->
                ErrorState(
                    message = state.error,
                    modifier = Modifier.padding(top = Spacing.lg),
                    retryLabel = "Try again",
                    onRetry = onRetry,
                )

            summary == null ->
                EmptyState(
                    title = stringResource(R.string.progress_none),
                    message = "Study a few cards or take a quiz and your progress will appear here.",
                    modifier = Modifier.padding(top = Spacing.lg),
                )

            else -> ProgressBody(summary = summary)
        }

        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

@Composable
private fun ProgressBody(summary: ProgressSummaryDto) {
    StreakCard(summary)

    Spacer(modifier = Modifier.height(Spacing.md))

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StatTile(
            label = stringResource(R.string.progress_minutes),
            value = summary.totals.minutesStudied.toString(),
            icon = Icons.Filled.Insights,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.progress_active_days),
            value = summary.totals.activeDays.toString(),
            icon = Icons.Filled.LocalFireDepartment,
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(modifier = Modifier.height(Spacing.md))

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StatTile(
            label = stringResource(R.string.progress_quiz_attempts),
            value = summary.totals.quizAttempts.toString(),
            icon = Icons.Filled.Quiz,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.progress_avg_score),
            value =
                summary.totals.quizAverageScore
                    ?.let { "${(it * PERCENT_SCALE).toInt()}%" }
                    ?: "Not yet",
            icon = Icons.Filled.School,
            modifier = Modifier.weight(1f),
        )
    }

    if (summary.vocabulary.tracked > 0) {
        SectionHeader(title = stringResource(R.string.progress_vocab_mastery), modifier = Modifier.padding(top = Spacing.lg))
        VocabularyCard(summary)
    }

    if (summary.recentActivity.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.progress_last_14_days), modifier = Modifier.padding(top = Spacing.lg))
        ActivityCard(summary.recentActivity)
    }

    if (summary.weakTopics.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.progress_worth_revisiting), modifier = Modifier.padding(top = Spacing.lg))
        WeakTopicsCard(summary)
    }
}

@Composable
private fun StreakCard(summary: ProgressSummaryDto) {
    LinguaCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.progress_streak_current),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (summary.streak.current == 1) "1 day" else "${summary.streak.current} days",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text =
                        buildString {
                            append("Longest: ${summary.streak.longest}")
                            summary.streak.lastActiveDate?.let { append(" · last active $it") }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    LinguaCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VocabularyCard(summary: ProgressSummaryDto) {
    val vocab = summary.vocabulary
    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            MasteryRow("Mastered", vocab.mastered, vocab.tracked, MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(Spacing.sm))
            MasteryRow("Learning", vocab.learning, vocab.tracked, MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(Spacing.sm))
            MasteryRow("New", vocab.fresh, vocab.tracked, MaterialTheme.colorScheme.tertiary)

            if (vocab.dueForReview > 0) {
                Text(
                    text = stringResource(R.string.progress_due_review, vocab.dueForReview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun MasteryRow(
    label: String,
    count: Int,
    total: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.progress_count_format, count, total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else count.toFloat() / total.toFloat() },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun ActivityCard(days: List<ActivityDayDto>) {
    val peak = days.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    val fraction = day.minutes.toFloat() / peak.toFloat()
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(CHART_MAX_BAR_HEIGHT_DP.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(
                                            (CHART_MAX_BAR_HEIGHT_DP * fraction)
                                                .coerceAtLeast(if (day.minutes > 0) CHART_MIN_BAR_HEIGHT_DP else 2f)
                                                .dp,
                                        ).clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (day.minutes > 0) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        ),
                            )
                        }
                        Text(
                            text = day.date.takeLast(2),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeakTopicsCard(summary: ProgressSummaryDto) {
    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            summary.weakTopics.forEachIndexed { index, topic ->
                if (index > 0) Spacer(modifier = Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.tertiary),
                    )
                    Text(
                        text = topic.topic,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(start = Spacing.sm),
                    )
                    Text(
                        text = "×${topic.occurrences}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

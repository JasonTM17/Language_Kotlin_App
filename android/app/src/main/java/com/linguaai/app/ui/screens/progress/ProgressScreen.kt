package com.linguaai.app.ui.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.linguaai.app.ui.theme.BrandGradients
import com.linguaai.app.ui.theme.HeatmapDark0
import com.linguaai.app.ui.theme.HeatmapDark1
import com.linguaai.app.ui.theme.HeatmapDark2
import com.linguaai.app.ui.theme.HeatmapDark3
import com.linguaai.app.ui.theme.HeatmapDark4
import com.linguaai.app.ui.theme.HeatmapLight0
import com.linguaai.app.ui.theme.HeatmapLight1
import com.linguaai.app.ui.theme.HeatmapLight2
import com.linguaai.app.ui.theme.HeatmapLight3
import com.linguaai.app.ui.theme.HeatmapLight4
import com.linguaai.app.ui.theme.Spacing
import com.linguaai.app.ui.util.render
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The server reports quiz accuracy as a 0..1 ratio; the UI shows a percentage. */
private const val PERCENT_SCALE = 100

/** Cells per heatmap row: one grid week. */
private const val HEATMAP_COLUMNS = 7

/** Minute thresholds bounding the heatmap intensity ladder (exclusive top). */
private const val HEATMAP_BUCKET_1 = 5
private const val HEATMAP_BUCKET_2 = 15
private const val HEATMAP_BUCKET_3 = 25

/** Achievement thresholds; badges are derived, never persisted. */
private const val BADGE_STREAK_3 = 3
private const val BADGE_STREAK_7 = 7
private const val BADGE_MASTERED_50 = 50
private const val BADGE_QUIZ_AVG = 0.8
private const val BADGE_MINUTES_60 = 60

/** Heatmap ladder indices, shared by the bucket mapper and the colour ladder. */
private const val LADDER_EMPTY = 0
private const val LADDER_LIGHT = 1
private const val LADDER_MILD = 2
private const val LADDER_WARM = 3
private const val LADDER_HOT = 4

/** Pure minutes→ladder bucket (0..4) for the activity heatmap. */
internal fun intensityBucket(minutes: Int): Int =
    when {
        minutes <= 0 -> LADDER_EMPTY
        minutes < HEATMAP_BUCKET_1 -> LADDER_LIGHT
        minutes < HEATMAP_BUCKET_2 -> LADDER_MILD
        minutes < HEATMAP_BUCKET_3 -> LADDER_WARM
        else -> LADDER_HOT
    }

@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    onAskTutor: (String) -> Unit = {},
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    ProgressContent(
        state = state,
        isOnline = isOnline,
        onRetry = viewModel::refresh,
        onAskTutor = onAskTutor,
        modifier = modifier,
    )
}

@Composable
private fun ProgressContent(
    state: ProgressUiState,
    isOnline: Boolean,
    onRetry: () -> Unit,
    onAskTutor: (String) -> Unit,
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
                    message = state.error.render(),
                    modifier = Modifier.padding(top = Spacing.lg),
                    retryLabel = stringResource(R.string.common_retry),
                    onRetry = onRetry,
                )

            summary == null ->
                EmptyState(
                    title = stringResource(R.string.progress_none),
                    message = stringResource(R.string.progress_empty_hint),
                    modifier = Modifier.padding(top = Spacing.lg),
                )

            else -> ProgressBody(summary = summary, onAskTutor = onAskTutor)
        }

        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

@Composable
private fun ProgressBody(
    summary: ProgressSummaryDto,
    onAskTutor: (String) -> Unit,
) {
    StreakHero(summary)

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
                    ?: stringResource(R.string.progress_not_yet),
            icon = Icons.Filled.School,
            modifier = Modifier.weight(1f),
        )
    }

    if (summary.recentActivity.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.progress_last_14_days), modifier = Modifier.padding(top = Spacing.lg))
        ActivityHeatmapCard(summary.recentActivity)
    }

    BadgeStrip(summary)

    if (summary.vocabulary.tracked > 0) {
        SectionHeader(title = stringResource(R.string.progress_vocab_mastery), modifier = Modifier.padding(top = Spacing.lg))
        VocabularyCard(summary)
    }

    if (summary.weakTopics.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.progress_worth_revisiting), modifier = Modifier.padding(top = Spacing.lg))
        WeakTopicsCard(summary, onAskTutor)
    }
}

/** Streak as the screen hero: gradient card, flame disc, longest-streak line. */
@Composable
private fun StreakHero(summary: ProgressSummaryDto) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
                .clip(MaterialTheme.shapes.large)
                .background(BrandGradients.hero()),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.progress_streak_current),
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandGradients.OnHeroMuted,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = summary.streak.current.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        color = BrandGradients.OnHero,
                    )
                    Text(
                        text =
                            stringResource(
                                R.plurals.progress_days_unit,
                                summary.streak.current,
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        color = BrandGradients.OnHeroMuted,
                        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.sm),
                    )
                }
                Text(
                    text = stringResource(R.string.progress_longest, summary.streak.longest),
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandGradients.OnHeroMuted,
                )
                summary.streak.lastActiveDate?.let { lastActive ->
                    Text(
                        text = stringResource(R.string.progress_last_active, lastActive),
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandGradients.OnHeroMuted,
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(BrandGradients.OnHero.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = BrandGradients.OnHeroAmber,
                    modifier = Modifier.size(34.dp),
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

/** Two-row circle heatmap over the server's most recent activity window. */
@Composable
private fun ActivityHeatmapCard(days: List<ActivityDayDto>) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val ladder =
        if (isDark) {
            listOf(HeatmapDark0, HeatmapDark1, HeatmapDark2, HeatmapDark3, HeatmapDark4)
        } else {
            listOf(HeatmapLight0, HeatmapLight1, HeatmapLight2, HeatmapLight3, HeatmapLight4)
        }
    val weekdayColor = MaterialTheme.colorScheme.onSurfaceVariant

    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            rows(days).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    row.forEach { day ->
                        val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(ladder[intensityBucket(day.minutes)]),
                            )
                            Text(
                                text =
                                    date
                                        ?.dayOfWeek
                                        ?.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                                        .orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = weekdayColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                    }
                    // Pad a short trailing row so circles keep their column width.
                    repeat(HEATMAP_COLUMNS - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            HeatmapLegend(ladder.first(), ladder.last())
        }
    }
}

/** Splits the window into grid rows of [HEATMAP_COLUMNS] days, date order preserved. */
private fun rows(days: List<ActivityDayDto>): List<List<ActivityDayDto>> = days.chunked(HEATMAP_COLUMNS)

@Composable
private fun HeatmapLegend(
    emptyColor: Color,
    fullColor: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(emptyColor),
        )
        Text(
            text = stringResource(R.string.progress_heatmap_legend_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.progress_heatmap_legend_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(fullColor)
                    .padding(start = Spacing.xs),
        )
    }
}

/** One derived badge: a pure predicate over the summary plus display metadata. */
private data class BadgeSpec(
    val nameRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val earned: (ProgressSummaryDto) -> Boolean,
)

private val BADGE_SPECS =
    listOf(
        BadgeSpec(R.string.badge_flame_name, R.string.badge_flame_desc, Icons.Filled.LocalFireDepartment) {
            it.streak.current >= BADGE_STREAK_3
        },
        BadgeSpec(R.string.badge_blaze_name, R.string.badge_blaze_desc, Icons.Filled.LocalFireDepartment) {
            it.streak.current >= BADGE_STREAK_7
        },
        BadgeSpec(R.string.badge_vocab_name, R.string.badge_vocab_desc, Icons.AutoMirrored.Filled.MenuBook) {
            it.vocabulary.mastered >= BADGE_MASTERED_50
        },
        BadgeSpec(R.string.badge_quiz_name, R.string.badge_quiz_desc, Icons.Filled.Quiz) {
            (it.totals.quizAverageScore ?: 0.0) >= BADGE_QUIZ_AVG
        },
        BadgeSpec(R.string.badge_time_name, R.string.badge_time_desc, Icons.Filled.Timer) {
            it.totals.minutesStudied >= BADGE_MINUTES_60
        },
        BadgeSpec(R.string.badge_ai_name, R.string.badge_ai_desc, Icons.Filled.AutoAwesome) {
            it.totals.aiConversations >= 1
        },
    )

/** Derived, unpersisted badge gallery rendered from the same summary. */
@Composable
private fun BadgeStrip(summary: ProgressSummaryDto) {
    val earnedCount = BADGE_SPECS.count { it.earned(summary) }
    SectionHeader(title = stringResource(R.string.progress_badges_title), modifier = Modifier.padding(top = Spacing.lg))
    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.progress_badge_count, earnedCount, BADGE_SPECS.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            BADGE_SPECS.chunked(BADGE_STRIP_COLUMNS).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    row.forEach { spec ->
                        BadgeTile(
                            spec = spec,
                            earned = spec.earned(summary),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(BADGE_STRIP_COLUMNS - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private const val BADGE_STRIP_COLUMNS = 3

@Composable
private fun BadgeTile(
    spec: BadgeSpec,
    earned: Boolean,
    modifier: Modifier = Modifier,
) {
    val container =
        if (earned) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        }
    val content =
        if (earned) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = stringResource(spec.nameRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = stringResource(spec.descRes),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VocabularyCard(summary: ProgressSummaryDto) {
    val vocab = summary.vocabulary
    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            MasteryRow(
                label = stringResource(R.string.progress_mastered),
                count = vocab.mastered,
                total = vocab.tracked,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            MasteryRow(
                label = stringResource(R.string.progress_learning),
                count = vocab.learning,
                total = vocab.tracked,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            MasteryRow(
                label = stringResource(R.string.progress_new),
                count = vocab.fresh,
                total = vocab.tracked,
                color = MaterialTheme.colorScheme.tertiary,
            )

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
    color: Color,
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
private fun WeakTopicsCard(
    summary: ProgressSummaryDto,
    onAskTutor: (String) -> Unit,
) {
    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.progress_open_tutor),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            summary.weakTopics.forEachIndexed { index, topic ->
                if (index > 0) Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onAskTutor(topic.topic) },
                ) {
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

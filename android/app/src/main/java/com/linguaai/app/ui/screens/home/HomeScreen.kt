package com.linguaai.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.repository.ProfileData
import com.linguaai.app.ui.components.ErrorState
import com.linguaai.app.ui.components.IconTile
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.components.SectionHeader
import com.linguaai.app.ui.theme.Spacing

@Composable
fun HomeScreen(
    onContinueLesson: (Long) -> Unit,
    onStartReview: () -> Unit,
    onOpenAiTutor: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> LoadingIndicator()
        state.profile == null && state.error != null ->
            ErrorState(
                message = state.error.orEmpty(),
                retryLabel = "Retry",
                onRetry = viewModel::refresh,
            )
        else -> HomeContent(state, onContinueLesson, onStartReview, onOpenAiTutor)
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onContinueLesson: (Long) -> Unit,
    onStartReview: () -> Unit,
    onOpenAiTutor: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
    ) {
        OfflineBanner(visible = state.isOffline, modifier = Modifier.padding(top = Spacing.sm))
        HomeHeader(state.profile, state.languageName)
        DailyGoalCard(state)
        ContinueLearningCard(state.continueLesson, onContinueLesson)
        ReviewCard(state.dueVocabularyCount, onStartReview)
        AiTutorCard(onOpenAiTutor)

        state.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        Box(modifier = Modifier.height(Spacing.lg))
    }
}

/** Avatar, greeting and the learner's language, shown at the top of the screen. */
@Composable
private fun HomeHeader(
    profile: ProfileData?,
    languageName: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.lg),
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    profile
                        ?.user
                        ?.username
                        ?.take(1)
                        ?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = Spacing.md),
        ) {
            Text(
                text = greeting() + (profile?.user?.username?.let { ", $it" } ?: ""),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    when {
                        profile?.level != null && languageName != null -> "$languageName · ${profile.level}"
                        profile?.level != null -> "Level ${profile.level}"
                        else -> "Choose a goal to get started"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/** Today's minutes with a numeric progress ring and a quiet streak accent. */
@Composable
private fun DailyGoalCard(state: HomeUiState) {
    val fraction =
        if (state.dailyGoalMinutes > 0) {
            (state.todayMinutes.toFloat() / state.dailyGoalMinutes).coerceIn(0f, 1f)
        } else {
            0f
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.lg),
        shape = MaterialTheme.shapes.large,
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier =
                Modifier.background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                            ),
                    ),
                ),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier = Modifier.size(76.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface,
                        strokeWidth = 8.dp,
                    )
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Daily goal",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "${state.streakDays} day streak",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                    Text(
                        text = "${state.todayMinutes} of ${state.dailyGoalMinutes} minutes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
    }
}

/** The lesson the learner left off on, or a calm preview of the catalogue ahead. */
@Composable
private fun ContinueLearningCard(
    lesson: LessonSummaryDto?,
    onContinueLesson: (Long) -> Unit,
) {
    SectionHeader(title = "Continue learning", modifier = Modifier.padding(top = Spacing.lg))
    if (lesson != null) {
        LinguaCard(onClick = { onContinueLesson(lesson.id) }) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                IconTile(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(lesson.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(
                        text =
                            "${lesson.type.lowercase().replaceFirstChar { it.uppercase() }} · " +
                                "${lesson.estimatedMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Text(
                        text = "Continue lesson",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    } else {
        LinguaCard {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                IconTile(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    Text("No lessons available yet", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Your first lesson will appear here soon.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** How many words are due, and the way into reviewing them. */
@Composable
private fun ReviewCard(
    dueVocabularyCount: Int,
    onStartReview: () -> Unit,
) {
    SectionHeader(title = "Review words", modifier = Modifier.padding(top = Spacing.lg))
    LinguaCard(onClick = onStartReview) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            IconTile(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        if (dueVocabularyCount > 0) {
                            "$dueVocabularyCount words ready"
                        } else {
                            "Nothing due right now"
                        },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Review flashcards to keep your memory fresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Shortcut into the AI tutor from the dashboard. */
@Composable
private fun AiTutorCard(onOpenAiTutor: () -> Unit) {
    SectionHeader(title = "AI Tutor", modifier = Modifier.padding(top = Spacing.lg))
    LinguaCard(onClick = onOpenAiTutor, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            IconTile(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Ask your tutor",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Get a clear explanation or practice a sentence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Local-time bands the greeting is chosen from. */
private val MORNING_HOURS = 5..11
private val AFTERNOON_HOURS = 12..17

private fun greeting(): String {
    val hour =
        java.util.Calendar
            .getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in MORNING_HOURS -> "Good morning"
        in AFTERNOON_HOURS -> "Good afternoon"
        else -> "Good evening"
    }
}

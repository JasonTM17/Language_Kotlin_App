package com.linguaai.app.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.repository.ProfileData
import com.linguaai.app.ui.components.ErrorState
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
        HomeHeader(state.profile)

        DailyGoalCard(state)

        ContinueLearningCard(state.continueLesson, onContinueLesson)

        ReviewCard(state.dueVocabularyCount, onStartReview)

        AiTutorCard(onOpenAiTutor)

        Text(
            text = state.error ?: "",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Box(modifier = Modifier.height(Spacing.lg))
    }
}

/** Avatar, greeting and the learner's language, shown at the top of the screen. */
@Composable
private fun HomeHeader(profile: ProfileData?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
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
        Column(modifier = Modifier.padding(start = Spacing.md)) {
            Text(
                text = greeting() + (profile?.user?.username?.let { ", $it" } ?: "") + " 👋",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text =
                    listOfNotNull(
                        profile?.level?.let { level -> languageLabel(profile) + " $level" },
                    ).joinToString("").ifEmpty { "Set your language in Profile" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Today's minutes against the daily goal, with the streak beside it. */
@Composable
private fun DailyGoalCard(state: HomeUiState) {
    val fraction =
        if (state.dailyGoalMinutes > 0) {
            (state.todayMinutes.toFloat() / state.dailyGoalMinutes).coerceIn(0f, 1f)
        } else {
            0f
        }

    LinguaCard(modifier = Modifier.padding(top = Spacing.md)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Daily goal",
                    style = MaterialTheme.typography.titleSmall,
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
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
            Text(
                text = "${state.todayMinutes} / ${state.dailyGoalMinutes} minutes today",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Box(
                modifier =
                    Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** The lesson the learner left off on, or a nudge to finish onboarding. */
@Composable
private fun ContinueLearningCard(
    lesson: LessonSummaryDto?,
    onContinueLesson: (Long) -> Unit,
) {
    SectionHeader(title = "Continue learning", modifier = Modifier.padding(top = Spacing.lg))
    if (lesson != null) {
        LinguaCard(onClick = { onContinueLesson(lesson.id) }) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text =
                        "${lesson.type.lowercase().replaceFirstChar { it.uppercase() }} · " +
                            "${lesson.estimatedMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    Text(
                        text = "Open lesson",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .padding(start = Spacing.xs)
                                .size(16.dp),
                    )
                }
            }
        }
    } else {
        LinguaCard {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text("No lessons available yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Complete onboarding to pick a language.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    SectionHeader(title = "Review", modifier = Modifier.padding(top = Spacing.md))
    LinguaCard(onClick = onStartReview) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        if (dueVocabularyCount > 0) {
                            "$dueVocabularyCount words due for review"
                        } else {
                            "Nothing due right now"
                        },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Review flashcards to grow your streak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Shortcut into the AI tutor from the dashboard. */
@Composable
private fun AiTutorCard(onOpenAiTutor: () -> Unit) {
    SectionHeader(title = "AI Tutor", modifier = Modifier.padding(top = Spacing.md))
    LinguaCard(onClick = onOpenAiTutor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.md),
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text("Ask your AI tutor", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Grammar questions, corrections and practice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun languageLabel(profile: ProfileData): String =
    when (profile.languageId) {
        1L -> "Japanese"
        2L -> "English"
        else -> "Language"
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

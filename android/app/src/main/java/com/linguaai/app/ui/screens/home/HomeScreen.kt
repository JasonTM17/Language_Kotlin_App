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
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.repository.ProfileData
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.ui.components.ErrorState
import com.linguaai.app.ui.components.IconTile
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.components.SectionHeader
import com.linguaai.app.ui.theme.BrandGradients
import com.linguaai.app.ui.theme.Spacing
import com.linguaai.app.ui.util.render

@Composable
fun HomeScreen(
    onContinueLesson: (Long) -> Unit,
    onStartReview: () -> Unit,
    onOpenAiTutor: () -> Unit,
    onOpenVocabulary: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> LoadingIndicator()
        state.profile == null && state.error != null ->
            ErrorState(
                message = state.error?.render().orEmpty(),
                retryLabel = stringResource(R.string.common_retry),
                onRetry = viewModel::refresh,
            )
        else -> HomeContent(state, onContinueLesson, onStartReview, onOpenAiTutor, onOpenVocabulary)
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onContinueLesson: (Long) -> Unit,
    onStartReview: () -> Unit,
    onOpenAiTutor: () -> Unit,
    onOpenVocabulary: () -> Unit,
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
        WordOfDayCard(state.wordOfDay, onOpenVocabulary)
        ReviewCard(state.dueVocabularyCount, onStartReview)
        AiTutorCard(onOpenAiTutor)

        state.error?.let { error ->
            Text(
                text = error.render(),
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
            val firstName = profile?.user?.username
            Text(
                text =
                    when (hour()) {
                        in MORNING_HOURS -> stringResource(R.string.home_greeting_morning, firstName ?: "")
                        in AFTERNOON_HOURS -> stringResource(R.string.home_greeting_afternoon, firstName ?: "")
                        else -> stringResource(R.string.home_greeting_evening, firstName ?: "")
                    },
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
        Box(modifier = Modifier.background(BrandGradients.hero())) {
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
                        color = BrandGradients.OnHero,
                        trackColor = BrandGradients.OnHero.copy(alpha = 0.35f),
                        strokeWidth = 8.dp,
                    )
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = BrandGradients.OnHero,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.home_daily_goal),
                            style = MaterialTheme.typography.titleMedium,
                            color = BrandGradients.OnHero,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = BrandGradients.OnHeroAmber,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.home_day_streak, state.streakDays),
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandGradients.OnHero,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_minutes_progress, state.todayMinutes, state.dailyGoalMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandGradients.OnHeroMuted,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
    }
}

/** The lesson the learner left off on, rendered as the screen's hero card. */
@Composable
private fun ContinueLearningCard(
    lesson: LessonSummaryDto?,
    onContinueLesson: (Long) -> Unit,
) {
    SectionHeader(title = stringResource(R.string.home_continue_learning), modifier = Modifier.padding(top = Spacing.lg))
    if (lesson != null) {
        Surface(
            onClick = { onContinueLesson(lesson.id) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            shadowElevation = 2.dp,
        ) {
            Box(modifier = Modifier.background(BrandGradients.hero())) {
                Row(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(BrandGradients.OnHero.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = BrandGradients.OnHero,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            lesson.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = BrandGradients.OnHero,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.home_lesson_type_minutes,
                                    lesson.type.lowercase().replaceFirstChar { it.uppercase() },
                                    lesson.estimatedMinutes,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandGradients.OnHeroMuted,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                        Text(
                            text = stringResource(R.string.home_continue_lesson),
                            style = MaterialTheme.typography.labelLarge,
                            color = BrandGradients.OnHeroAmber,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = BrandGradients.OnHero,
                    )
                }
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
                    Text(stringResource(R.string.home_no_lessons), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.home_first_lesson_soon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Today's word from the learner's tracked vocabulary; tap opens the catalogue. */
@Composable
private fun WordOfDayCard(
    word: VocabularyCard?,
    onOpenVocabulary: () -> Unit,
) {
    if (word == null) return
    SectionHeader(title = stringResource(R.string.home_word_of_day), modifier = Modifier.padding(top = Spacing.lg))
    LinguaCard(onClick = onOpenVocabulary) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            IconTile(
                imageVector = Icons.Filled.WbSunny,
                contentDescription = null,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(word.word, style = MaterialTheme.typography.titleMedium)
                word.reading?.let { reading ->
                    Text(
                        text = reading,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = word.meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs),
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

/** How many words are due, and the way into reviewing them. */
@Composable
private fun ReviewCard(
    dueVocabularyCount: Int,
    onStartReview: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.home_review_words), modifier = Modifier.padding(top = Spacing.lg))
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
                        stringResource(R.string.home_words_ready, dueVocabularyCount).takeIf { dueVocabularyCount > 0 }
                            ?: stringResource(R.string.home_nothing_due),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.home_review_hint),
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
    SectionHeader(title = stringResource(R.string.home_ai_tutor), modifier = Modifier.padding(top = Spacing.lg))
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
                    stringResource(R.string.home_ask_tutor),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.home_ask_tutor_hint),
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

private fun currentHour(): Int =
    java.util.Calendar
        .getInstance()
        .get(java.util.Calendar.HOUR_OF_DAY)

private fun hour(): Int = currentHour()

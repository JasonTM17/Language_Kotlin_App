package com.linguaai.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

private data class HomeActions(
    val onContinueLesson: (Long) -> Unit,
    val onStartReview: () -> Unit,
    val onOpenAiTutor: () -> Unit,
    val onOpenVocabulary: () -> Unit,
    val onAskAiWord: ((VocabularyCard) -> Unit)?,
    val onStartQuest: (com.linguaai.app.domain.model.DailyQuestType, VocabularyCard?) -> Unit,
    val onClaimQuest: (com.linguaai.app.domain.model.DailyQuestType) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onPracticeWord: () -> Unit,
)

private object HomeColors {
    val WordOfDayBadgeBackground = Color(0xFFFEF3C7)
    val WordOfDayBadgeBorder = Color(0xFFFDE68A)
    val WordOfDayBadgeIcon = Color(0xFFD97706)
    val WordOfDayBadgeText = Color(0xFF92400E)
    val AiQuest = Color(0xFF8B5CF6)
    val FlashcardsQuest = Color(0xFF0EA5E9)
    val QuizQuest = Color(0xFFF59E0B)
    val WordOfDayQuest = Color(0xFF10B981)
    val ClaimedQuestBackground = Color(0xFFECFDF5)
    val ClaimedQuestText = Color(0xFF047857)
    val Favorite = Color(0xFFF59E0B)
}

@Composable
fun HomeScreen(
    onContinueLesson: (Long) -> Unit,
    onStartReview: () -> Unit,
    onStartQuest: (com.linguaai.app.domain.model.DailyQuestType, VocabularyCard?) -> Unit,
    onOpenAiTutor: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onAskAiWord: ((VocabularyCard) -> Unit)? = null,
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
        else ->
            HomeContent(
                state = state,
                actions =
                    HomeActions(
                        onContinueLesson = onContinueLesson,
                        onStartReview = onStartReview,
                        onOpenAiTutor = onOpenAiTutor,
                        onOpenVocabulary = onOpenVocabulary,
                        onAskAiWord = onAskAiWord,
                        onStartQuest = onStartQuest,
                        onClaimQuest = viewModel::claimQuest,
                        onToggleFavorite = viewModel::toggleFavoriteWordOfDay,
                        onPracticeWord = viewModel::practiceWordOfDay,
                    ),
            )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    actions: HomeActions,
) {
    val tts =
        com.linguaai.app.ui.util
            .rememberLinguaTts(state.languageCode)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
    ) {
        OfflineBanner(visible = state.isOffline, modifier = Modifier.padding(top = Spacing.sm))
        HomeHeader(state.profile, state.languageName, state.userXp)
        DailyGoalCard(state)
        if (state.dailyQuests.isNotEmpty()) {
            DailyQuestsCard(
                quests = state.dailyQuests,
                onClaimQuest = actions.onClaimQuest,
                onStartQuest = { questType ->
                    startDailyQuest(
                        questType = questType,
                        wordOfDay = state.wordOfDay,
                        onPracticeWord = actions.onPracticeWord,
                        onNavigate = actions.onStartQuest,
                    )
                },
            )
        }
        ContinueLearningCard(state.continueLesson, actions.onContinueLesson)
        WordOfDayCard(
            word = state.wordOfDay,
            onOpenVocabulary = actions.onOpenVocabulary,
            onSpeak = { card ->
                val reading = card.reading?.takeIf { state.languageCode.equals("ja", ignoreCase = true) && it.isNotBlank() }
                tts.speak(card.word, state.languageCode, reading ?: card.word)
            },
            onToggleFavorite = actions.onToggleFavorite,
            onAskAi = { word ->
                actions.onPracticeWord()
                if (actions.onAskAiWord != null) {
                    actions.onAskAiWord(word)
                } else {
                    actions.onOpenAiTutor()
                }
            },
        )
        ReviewCard(state.dueVocabularyCount, actions.onStartReview)
        AiTutorCard(actions.onOpenAiTutor)

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

/** Avatar, greeting, learner's language and user XP badge, shown at the top of the screen. */
@Composable
private fun HomeHeader(
    profile: ProfileData?,
    languageName: String?,
    userXp: Int,
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
        Surface(
            color =
                HomeColors.WordOfDayBadgeBackground,
            shape = CircleShape,
            border =
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    HomeColors.WordOfDayBadgeBorder,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint =
                        HomeColors.WordOfDayBadgeIcon,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.user_xp_badge, userXp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color =
                        HomeColors.WordOfDayBadgeText,
                )
            }
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

/** Daily quests and XP challenges card. */
@Composable
internal fun DailyQuestsCard(
    quests: List<com.linguaai.app.domain.model.DailyQuest>,
    onClaimQuest: (com.linguaai.app.domain.model.DailyQuestType) -> Unit,
    onStartQuest: (com.linguaai.app.domain.model.DailyQuestType) -> Unit,
) {
    SectionHeader(
        title = stringResource(R.string.daily_quests_title),
        modifier = Modifier.padding(top = Spacing.lg),
    )
    LinguaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.daily_quests_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            quests.forEach { quest ->
                DailyQuestRow(
                    quest = quest,
                    onClaim = { onClaimQuest(quest.type) },
                    onStart = { onStartQuest(quest.type) },
                )
            }
        }
    }
}

@Composable
private fun DailyQuestRow(
    quest: com.linguaai.app.domain.model.DailyQuest,
    onClaim: () -> Unit,
    onStart: () -> Unit,
) {
    val (icon, tint) =
        when (quest.type) {
            com.linguaai.app.domain.model.DailyQuestType.AI_CHAT ->
                Icons.Filled.AutoAwesome to HomeColors.AiQuest
            com.linguaai.app.domain.model.DailyQuestType.FLASHCARDS ->
                Icons.AutoMirrored.Filled.MenuBook to HomeColors.FlashcardsQuest
            com.linguaai.app.domain.model.DailyQuestType.QUIZ ->
                Icons.Filled.CheckCircle to HomeColors.QuizQuest
            com.linguaai.app.domain.model.DailyQuestType.WORD_OF_DAY ->
                Icons.Filled.WbSunny to HomeColors.WordOfDayQuest
        }
    val titleRes =
        when (quest.type) {
            com.linguaai.app.domain.model.DailyQuestType.AI_CHAT -> R.string.quest_ai_chat_title
            com.linguaai.app.domain.model.DailyQuestType.FLASHCARDS -> R.string.quest_flashcards_title
            com.linguaai.app.domain.model.DailyQuestType.QUIZ -> R.string.quest_quiz_title
            com.linguaai.app.domain.model.DailyQuestType.WORD_OF_DAY -> R.string.quest_word_of_day_title
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "${quest.progress}/${quest.type.target}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { quest.progressFraction },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .padding(top = 4.dp)
                        .clip(CircleShape),
                color = tint,
                trackColor = tint.copy(alpha = 0.2f),
            )
        }
        DailyQuestAction(
            quest = quest,
            questTitleRes = titleRes,
            onClaim = onClaim,
            onStart = onStart,
        )
    }
}

@Composable
private fun DailyQuestAction(
    quest: com.linguaai.app.domain.model.DailyQuest,
    questTitleRes: Int,
    onClaim: () -> Unit,
    onStart: () -> Unit,
) {
    when {
        quest.isClaimed -> {
            Surface(
                shape = CircleShape,
                color = HomeColors.ClaimedQuestBackground,
            ) {
                Text(
                    text = stringResource(R.string.quest_claimed),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = HomeColors.ClaimedQuestText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        quest.isCompleted -> {
            androidx.compose.material3.Button(
                onClick = onClaim,
                shape = CircleShape,
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = HomeColors.QuizQuest,
                        contentColor = Color.White,
                    ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp),
            ) {
                Text(
                    text = stringResource(R.string.quest_claim_xp, quest.type.xpReward),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
        else -> {
            val actionText =
                stringResource(
                    if (quest.progress > 0) R.string.quest_continue else R.string.quest_start,
                )
            val questTitle = stringResource(questTitleRes)
            val accessibilityLabel =
                stringResource(R.string.quest_action_accessibility, actionText, questTitle)
            androidx.compose.material3.Button(
                onClick = onStart,
                shape = CircleShape,
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = accessibilityLabel },
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
    }
}

internal fun startDailyQuest(
    questType: com.linguaai.app.domain.model.DailyQuestType,
    wordOfDay: VocabularyCard?,
    onPracticeWord: () -> Unit,
    onNavigate: (com.linguaai.app.domain.model.DailyQuestType, VocabularyCard?) -> Unit,
) {
    if (questType == com.linguaai.app.domain.model.DailyQuestType.WORD_OF_DAY && wordOfDay != null) {
        onPracticeWord()
    }
    onNavigate(questType, wordOfDay)
}

/** Today's word from the learner's tracked vocabulary; tap opens the catalogue. */
@Composable
private fun WordOfDayCard(
    word: VocabularyCard?,
    onOpenVocabulary: () -> Unit,
    onSpeak: (VocabularyCard) -> Unit,
    onToggleFavorite: () -> Unit,
    onAskAi: (VocabularyCard) -> Unit,
) {
    if (word == null) return
    SectionHeader(title = stringResource(R.string.home_word_of_day), modifier = Modifier.padding(top = Spacing.lg))
    LinguaCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                IconTile(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = null,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(word.word, style = MaterialTheme.typography.titleMedium)
                        androidx.compose.material3.IconButton(
                            onClick = { onSpeak(word) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(R.string.tts_pronounce),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
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
                androidx.compose.material3.IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (word.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription =
                            stringResource(
                                if (word.favorite) R.string.home_word_unfavorite else R.string.home_word_favorite,
                            ),
                        tint =
                            if (word.favorite) {
                                HomeColors.Favorite
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }

            // Quick actions footer: Ask AI & View Vocabulary
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
            ) {
                androidx.compose.material3.AssistChip(
                    onClick = { onAskAi(word) },
                    label = { Text(stringResource(R.string.home_word_ask_ai)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.height(32.dp),
                )
                androidx.compose.material3.TextButton(
                    onClick = onOpenVocabulary,
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.vocab_title),
                        style = MaterialTheme.typography.labelMedium,
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

package com.linguaai.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.repository.ProfileData
import com.linguaai.app.ui.components.ErrorState
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LinguaOutlinedButton
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.SectionHeader
import com.linguaai.app.ui.theme.Spacing
import com.linguaai.app.ui.util.render

private val DAILY_GOAL_OPTIONS = listOf(10, 15, 20, 30, 45, 60)
private val REMINDER_HOURS = (6..22).toList()

@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignedOut()
    }

    ProfileContent(
        state = state,
        onDailyGoal = viewModel::setDailyGoal,
        onThemeMode = viewModel::setThemeMode,
        onReminderHour = { hour -> viewModel.setReminderTime(hour, state.reminderMinute) },
        onNotifications = viewModel::setNotificationsEnabled,
        onRetry = viewModel::load,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onDailyGoal: (Int) -> Unit,
    onThemeMode: (String) -> Unit,
    onReminderHour: (Int) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading && state.profile == null) {
        LoadingIndicator(modifier = modifier)
        return
    }

    if (state.profile == null && state.error != null) {
        // A failed profile fetch must not trap the user: retry is offered and
        // sign-out stays one tap away below the error.
        Column(modifier = modifier.fillMaxSize()) {
            ErrorState(
                message = state.error?.render().orEmpty(),
                modifier = Modifier.fillMaxWidth(),
                retryLabel = stringResource(R.string.common_retry),
                onRetry = onRetry,
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onSignOut,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.lg),
            ) {
                Text(stringResource(R.string.profile_sign_out))
            }
        }
        return
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
    ) {
        Column(modifier = Modifier.padding(top = Spacing.lg)) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.profile_keep_close),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        AccountCard(state.profile)

        DailyGoalSection(state.dailyGoalMinutes, onDailyGoal)
        AppearanceSection(state.themeMode, onThemeMode)
        RemindersSection(
            enabled = state.notificationsEnabled,
            reminderHour = state.reminderHour,
            onReminderHour = onReminderHour,
            onNotifications = onNotifications,
        )

        if (state.error != null) {
            Text(
                text = state.error.render(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        LinguaOutlinedButton(
            text = stringResource(R.string.profile_sign_out),
            onClick = onSignOut,
            enabled = !state.isSaving,
        )

        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

/** Avatar, username, email and the learner's current level. */
@Composable
private fun AccountCard(profile: ProfileData?) {
    LinguaCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        profile
                            ?.user
                            ?.username
                            ?.take(1)
                            ?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = Spacing.md),
            ) {
                Text(
                    text = profile?.user?.username ?: "Learner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profile?.user?.email.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val level = profile?.level
                if (level != null) {
                    Text(
                        text = stringResource(R.string.profile_level, level),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/** Chooser for the minutes the learner commits to each day. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DailyGoalSection(
    dailyGoalMinutes: Int,
    onDailyGoal: (Int) -> Unit,
) {
    SectionHeader(title = stringResource(R.string.profile_daily_goal), modifier = Modifier.padding(top = Spacing.lg))
    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.profile_minutes_a_day, dailyGoalMinutes),
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                modifier = Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                DAILY_GOAL_OPTIONS.forEach { minutes ->
                    FilterChip(
                        selected = dailyGoalMinutes == minutes,
                        onClick = { onDailyGoal(minutes) },
                        label = { Text("$minutes") },
                    )
                }
            }
        }
    }
}

/** Light, dark or follow-the-system. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(
    themeMode: String,
    onThemeMode: (String) -> Unit,
) {
    SectionHeader(title = stringResource(R.string.profile_appearance), modifier = Modifier.padding(top = Spacing.lg))
    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.DarkMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.profile_theme),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                listOf(
                    SettingsDataStore.THEME_SYSTEM to "System",
                    SettingsDataStore.THEME_LIGHT to "Light",
                    SettingsDataStore.THEME_DARK to "Dark",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

/** The study reminder toggle, and the hour it fires at when it is on. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RemindersSection(
    enabled: Boolean,
    reminderHour: Int,
    onReminderHour: (Int) -> Unit,
    onNotifications: (Boolean) -> Unit,
) {
    SectionHeader(title = stringResource(R.string.profile_reminders), modifier = Modifier.padding(top = Spacing.lg))
    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            val dailyReminderLabel = stringResource(R.string.profile_daily_reminder)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = dailyReminderLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = Spacing.sm),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onNotifications,
                    modifier = Modifier.semantics { contentDescription = dailyReminderLabel },
                )
            }

            if (enabled) {
                Text(
                    text = stringResource(R.string.profile_remind_at) + " %02d:00".format(reminderHour),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                FlowRow(
                    modifier = Modifier.padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    REMINDER_HOURS.forEach { hour ->
                        FilterChip(
                            selected = reminderHour == hour,
                            onClick = { onReminderHour(hour) },
                            label = { Text("%02d:00".format(hour)) },
                        )
                    }
                }
            }
        }
    }
}

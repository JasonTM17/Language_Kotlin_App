package com.linguaai.app.ui.screens.flashcard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.domain.srs.ReviewGrade
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.theme.LinguaMotion
import com.linguaai.app.ui.theme.Spacing
import com.linguaai.app.ui.util.render
import kotlinx.coroutines.launch

/** Horizontal drag (in dp) past which a swipe becomes a grade. */
private const val SWIPE_GRADE_THRESHOLD_DP = 110

/** Rotation degrees applied per dragged dp for the playful tilt. */
private const val SWIPE_TILT_PER_DP = 0.06f

/** Start angle of the answer side's flip-in reveal. */
private const val FLIP_START_DEGREES = -90f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    onBack: () -> Unit,
    viewModel: FlashcardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.flashcard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.padding(padding))
            state.finished ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyState(
                        title =
                            when {
                                state.error != null -> stringResource(R.string.flashcard_review_paused)
                                state.reviewedCount > 0 -> stringResource(R.string.flashcard_session_complete)
                                else -> stringResource(R.string.flashcard_nothing_due)
                            },
                        message =
                            state.error?.render()
                                ?: if (state.reviewedCount > 0) {
                                    stringResource(R.string.flashcard_session_summary, state.reviewedCount)
                                } else {
                                    stringResource(R.string.flashcard_all_caught_up)
                                },
                        actionLabel =
                            if (state.error != null) {
                                stringResource(R.string.common_back)
                            } else {
                                stringResource(R.string.common_done)
                            },
                        onAction = onBack,
                    )
                    if (state.error == null) {
                        Text(
                            text = stringResource(R.string.flashcard_cram_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = Spacing.md),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            modifier = Modifier.padding(top = Spacing.sm),
                        ) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.startCramSession(favoritesOnly = false) },
                            ) {
                                Text(stringResource(R.string.flashcard_cram_all))
                            }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.startCramSession(favoritesOnly = true) },
                            ) {
                                Text(stringResource(R.string.flashcard_cram_favorites))
                            }
                        }
                    }
                }
            else -> FlashcardContent(state, viewModel::onEvent, Modifier.padding(padding))
        }
    }
}

@Composable
private fun FlashcardContent(
    state: FlashcardUiState,
    onEvent: (FlashcardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = state.current ?: return
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { SWIPE_GRADE_THRESHOLD_DP.dp.toPx() }
    val dragX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // The answer side flips in from the spine instead of popping.
    val flip = remember { Animatable(0f) }
    LaunchedEffect(state.isRevealed) {
        if (state.isRevealed) {
            flip.snapTo(FLIP_START_DEGREES)
            flip.animateTo(0f, LinguaMotion.smooth())
        } else {
            flip.snapTo(0f)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.flashcard_card_position, state.currentIndex + 1, state.queue.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LinguaCard(
            modifier =
                Modifier
                    .padding(top = Spacing.md)
                    .graphicsLayer {
                        translationX = dragX.value
                        rotationZ = dragX.value * SWIPE_TILT_PER_DP
                    }.swipeToGrade(state, dragX, swipeThresholdPx, scope, onEvent),
            containerColor =
                if (state.isRevealed) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
        ) {
            AnimatedContent(targetState = state.isRevealed, label = "flashcard") { revealed ->
                FlashcardFace(
                    card = card,
                    languageCode = state.languageCode,
                    revealed = revealed,
                    flipDegrees = { flip.value },
                    onEvent = onEvent,
                )
            }
        }

        if (state.isRevealed) {
            Text(
                text = stringResource(R.string.flashcard_swipe_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
            )
            GradeActions(state, onEvent)
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}

/**
 * Horizontal swipe grading: drag right for GOOD, left for AGAIN, past
 * [swipeThresholdPx]. The card always springs back centred — grading advances
 * the queue, so the next card starts at rest.
 */
private fun Modifier.swipeToGrade(
    state: FlashcardUiState,
    dragX: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    swipeThresholdPx: Float,
    scope: kotlinx.coroutines.CoroutineScope,
    onEvent: (FlashcardEvent) -> Unit,
): Modifier =
    pointerInput(state.isRevealed, state.isSubmittingGrade) {
        if (!state.isRevealed || state.isSubmittingGrade) return@pointerInput
        var accumulated = 0f
        detectHorizontalDragGestures(
            onDragStart = { accumulated = 0f },
            onDragEnd = {
                if (accumulated > swipeThresholdPx) onEvent(FlashcardEvent.Grade(ReviewGrade.GOOD))
                if (accumulated < -swipeThresholdPx) onEvent(FlashcardEvent.Grade(ReviewGrade.AGAIN))
                scope.launch { dragX.animateTo(0f, LinguaMotion.smooth()) }
            },
            onDragCancel = {
                scope.launch { dragX.animateTo(0f, LinguaMotion.smooth()) }
            },
        ) { change, dragAmount ->
            change.consume()
            accumulated += dragAmount
            scope.launch { dragX.snapTo(dragX.value + dragAmount) }
        }
    }

/** One card face: prompt side, or the flip-in answer side once revealed. */
@Composable
private fun FlashcardFace(
    card: com.linguaai.app.domain.model.VocabularyCard,
    languageCode: String?,
    revealed: Boolean,
    flipDegrees: () -> Float,
    onEvent: (FlashcardEvent) -> Unit,
) {
    val tts =
        com.linguaai.app.ui.util
            .rememberLinguaTts(languageCode)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.xl)
                .graphicsLayer {
                    if (revealed) {
                        rotationY = flipDegrees()
                    }
                },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = card.word,
                style = MaterialTheme.typography.displaySmall,
                color =
                    if (revealed) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
            )
            IconButton(
                onClick = {
                    val reading = card.reading?.takeIf { languageCode.equals("ja", ignoreCase = true) && it.isNotBlank() }
                    tts.speak(card.word, languageCode, reading ?: card.word)
                },
                modifier = Modifier.padding(start = Spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.tts_pronounce),
                    tint =
                        if (revealed) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                )
            }
        }
        card.reading?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (revealed) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        if (revealed) {
            Text(
                text = card.meaning,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            card.example?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
            card.exampleTranslation?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        } else {
            LinguaButton(
                text = stringResource(R.string.flashcard_show_answer),
                onClick = { onEvent(FlashcardEvent.Reveal) },
                modifier = Modifier.padding(top = Spacing.xl),
            )
        }
    }
}

@Composable
private fun GradeActions(
    state: FlashcardUiState,
    onEvent: (FlashcardEvent) -> Unit,
) {
    val enabled = !state.isSubmittingGrade
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.padding(top = Spacing.md),
    ) {
        GradeButton(
            label = stringResource(R.string.flashcard_grade_again),
            grade = ReviewGrade.AGAIN,
            color = MaterialTheme.colorScheme.error,
            onEvent = onEvent,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        GradeButton(
            label = stringResource(R.string.flashcard_grade_hard),
            grade = ReviewGrade.HARD,
            color = MaterialTheme.colorScheme.tertiary,
            onEvent = onEvent,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.padding(top = Spacing.sm),
    ) {
        GradeButton(
            label = stringResource(R.string.flashcard_grade_good),
            grade = ReviewGrade.GOOD,
            color = MaterialTheme.colorScheme.primary,
            onEvent = onEvent,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        GradeButton(
            label = stringResource(R.string.flashcard_grade_easy),
            grade = ReviewGrade.EASY,
            color = MaterialTheme.colorScheme.secondary,
            onEvent = onEvent,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GradeButton(
    label: String,
    grade: ReviewGrade,
    color: androidx.compose.ui.graphics.Color,
    onEvent: (FlashcardEvent) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { onEvent(FlashcardEvent.Grade(grade)) },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(label)
    }
}

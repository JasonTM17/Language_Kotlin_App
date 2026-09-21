package com.linguaai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linguaai.app.R
import com.linguaai.app.ui.theme.Spacing

/** The app-wide filled action button with a consistent loading state. */
@Composable
fun LinguaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = text },
        enabled = enabled && !isLoading,
        shape = MaterialTheme.shapes.medium,
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Tonal secondary action used for supporting paths beside the main action. */
@Composable
fun LinguaTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = text },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Outlined secondary action with the same touch target as the primary CTA. */
@Composable
fun LinguaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = text },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Standard content card with unified elevation, hairline border and full width. */
@Composable
fun LinguaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp)
    val bordered =
        modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.medium,
            )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = bordered,
            colors = colors,
            elevation = elevation,
            shape = MaterialTheme.shapes.medium,
        ) { content() }
    } else {
        Card(
            modifier = bordered,
            colors = colors,
            elevation = elevation,
            shape = MaterialTheme.shapes.medium,
        ) { content() }
    }
}

/** Small tonal icon tile used to make cards scannable without extra decoration. */
@Composable
fun IconTile(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tileSize: Dp = 48.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier =
            modifier
                .size(tileSize)
                .clip(RoundedCornerShape(Spacing.sm))
                .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Full-width centered loading state. */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Friendly empty state with optional primary action. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    art: (@Composable () -> Unit)? = null,
) {
    val visual =
        when {
            art != null -> StateVisual.Art(art)
            icon != null -> StateVisual.Icon(icon, MaterialTheme.colorScheme.onSurfaceVariant)
            else -> null
        }
    StateScaffold(
        modifier = modifier,
        visual = visual,
        title = title,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

/** Error state with retry affordance. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    retryModifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    StateScaffold(
        modifier = modifier,
        visual = null,
        title = stringResource(R.string.state_something_wrong),
        message = message,
        actionLabel = retryLabel,
        actionModifier = retryModifier,
        onAction = onRetry,
    )
}

/** The leading visual of a state scaffold: a tinted icon or a full artwork slot. */
private sealed interface StateVisual {
    data class Icon(
        val imageVector: ImageVector,
        val tint: Color,
    ) : StateVisual

    data class Art(
        val content: @Composable () -> Unit,
    ) : StateVisual
}

@Composable
private fun StateScaffold(
    modifier: Modifier,
    visual: StateVisual?,
    title: String,
    message: String,
    actionLabel: String?,
    actionModifier: Modifier = Modifier,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (visual) {
            is StateVisual.Art -> visual.content()
            is StateVisual.Icon ->
                Icon(
                    imageVector = visual.imageVector,
                    contentDescription = null,
                    tint = visual.tint,
                    modifier = Modifier.size(48.dp),
                )
            null -> Unit
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        if (actionLabel != null && onAction != null) {
            OutlinedButton(
                onClick = onAction,
                modifier =
                    actionModifier
                        .heightIn(min = 48.dp)
                        .padding(top = Spacing.md),
            ) {
                Text(actionLabel)
            }
        }
    }
}

/** Section titles used across dashboard screens. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

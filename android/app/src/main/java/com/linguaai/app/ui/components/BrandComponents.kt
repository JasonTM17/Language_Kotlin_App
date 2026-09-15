package com.linguaai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val MARK_SIZE_RATIO = 0.76f

/**
 * LinguaAI's own mark: an open book inside a conversation bubble, with a
 * small clay spark for the AI companion. The geometry stays intentionally
 * simple so it remains recognizable in the launcher and at small sizes.
 */
@Composable
fun LinguaMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val moss = MaterialTheme.colorScheme.primary
    val clay = MaterialTheme.colorScheme.secondary
    val page =
        if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val ink =
        if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            moss
        }
    val semanticModifier =
        if (contentDescription == null) {
            modifier
        } else {
            modifier.semantics { this.contentDescription = contentDescription }
        }

    Canvas(semanticModifier) {
        drawLinguaMark(moss = moss, clay = clay, page = page, ink = ink)
    }
}

/** Brand tile used by the auth and splash lockups. */
@Composable
fun LinguaMarkTile(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tileSize: Dp = 72.dp,
) {
    val semanticModifier =
        if (contentDescription == null) {
            modifier
        } else {
            modifier.semantics { this.contentDescription = contentDescription }
        }
    Box(
        modifier =
            semanticModifier
                .size(tileSize)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        LinguaMark(modifier = Modifier.size(tileSize * MARK_SIZE_RATIO))
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawLinguaMark(
    moss: Color,
    clay: Color,
    page: Color,
    ink: Color,
) {
    val markScale = size.minDimension / 108f

    scale(markScale, pivot = Offset.Zero) {
        val bubble =
            Path().apply {
                moveTo(22f, 46f)
                cubicTo(22f, 36.6f, 29.6f, 29f, 39f, 29f)
                lineTo(69f, 29f)
                cubicTo(78.4f, 29f, 86f, 36.6f, 86f, 46f)
                lineTo(86f, 65f)
                cubicTo(86f, 74.4f, 78.4f, 82f, 69f, 82f)
                lineTo(62f, 82f)
                lineTo(54f, 91f)
                lineTo(46f, 82f)
                lineTo(39f, 82f)
                cubicTo(29.6f, 82f, 22f, 74.4f, 22f, 65f)
                close()
            }
        drawPath(bubble, moss)

        val leftPage =
            Path().apply {
                moveTo(29f, 48f)
                cubicTo(35f, 43f, 43f, 42f, 51f, 47f)
                lineTo(51f, 73f)
                cubicTo(43f, 68f, 35f, 68f, 29f, 73f)
                close()
            }
        drawPath(leftPage, page)

        val rightPage =
            Path().apply {
                moveTo(57f, 47f)
                cubicTo(65f, 42f, 73f, 43f, 79f, 48f)
                lineTo(79f, 73f)
                cubicTo(73f, 68f, 65f, 68f, 57f, 73f)
                close()
            }
        drawPath(rightPage, page)

        drawLine(
            color = clay,
            start = Offset(54f, 46f),
            end = Offset(54f, 75f),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ink,
            start = Offset(35f, 54f),
            end = Offset(46f, 54f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ink,
            start = Offset(62f, 54f),
            end = Offset(73f, 54f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ink,
            start = Offset(35f, 62f),
            end = Offset(44f, 62f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ink,
            start = Offset(62f, 62f),
            end = Offset(71f, 62f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round,
        )

        val spark =
            Path().apply {
                moveTo(54f, 15f)
                lineTo(57f, 21f)
                lineTo(63f, 24f)
                lineTo(57f, 27f)
                lineTo(54f, 33f)
                lineTo(51f, 27f)
                lineTo(45f, 24f)
                lineTo(51f, 21f)
                close()
            }
        drawPath(spark, clay)
    }
}

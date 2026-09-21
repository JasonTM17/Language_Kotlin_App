package com.linguaai.app.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Renders the narrow slice of markdown the tutor is actually asked to produce.
 *
 * `PromptBuilder.modeInstruction("sentence-correction")` mandates the shape
 * "**Corrected:** … **What changed:** one bullet per fix", so emphasis and
 * bullets are part of the server's output contract rather than an optional
 * nicety. Rendering that as a plain string shows the learner literal
 * asterisks, which is why this exists instead of a bare `Text`.
 *
 * Deliberately restricted to bold, inline code, bullets and fenced code blocks.
 * Tables, links, headings and nesting are left as literal text rather than
 * silently mis-rendered.
 */
sealed interface TutorBlock {
    data class Line(
        val text: AnnotatedString,
    ) : TutorBlock

    data class Bullet(
        val text: AnnotatedString,
    ) : TutorBlock

    data class Code(
        val text: String,
    ) : TutorBlock

    /**
     * A blank line in the source. Dropped paragraphs made a reply shaped like
     * "**Corrected:** … **What changed:** …" read as one cramped wall of text,
     * because the emphasis already removes the visual cue the author used.
     */
    data object Blank : TutorBlock
}

private val BULLET_MARKERS = listOf("- ", "* ", "• ")

fun parseTutorMarkdown(source: String): List<TutorBlock> {
    val blocks = mutableListOf<TutorBlock>()
    val fence = StringBuilder()
    var inFence = false

    for (rawLine in source.replace("\r\n", "\n").lines()) {
        val trimmed = rawLine.trimStart()
        if (trimmed.startsWith("```")) {
            if (inFence) {
                blocks += TutorBlock.Code(fence.toString().trimEnd('\n'))
                fence.setLength(0)
            }
            inFence = !inFence
        } else if (inFence) {
            if (fence.isNotEmpty()) fence.append('\n')
            fence.append(rawLine)
        } else if (rawLine.isBlank()) {
            if (blocks.isNotEmpty() && blocks.last() != TutorBlock.Blank) {
                blocks += TutorBlock.Blank
            }
        } else {
            val marker = BULLET_MARKERS.firstOrNull { trimmed.startsWith(it) }
            if (marker != null) {
                blocks += TutorBlock.Bullet(parseInline(trimmed.removePrefix(marker).trim()))
            } else {
                blocks += TutorBlock.Line(parseInline(trimmed))
            }
        }
    }

    while (blocks.isNotEmpty() && blocks.last() == TutorBlock.Blank) {
        blocks.removeAt(blocks.size - 1)
    }
    // An unbalanced fence is treated as code rather than swallowing the rest of
    // the reply, which is what a truncated model response looks like.
    if (inFence && fence.isNotEmpty()) {
        blocks += TutorBlock.Code(fence.toString().trimEnd('\n'))
    }
    return blocks
}

internal fun parseInline(text: String): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val end =
                when {
                    text.startsWith("**", i) -> text.indexOf("**", i + 2)
                    text[i] == '`' -> text.indexOf('`', i + 1)
                    else -> -1
                }
            if (text[i] != '`' && !text.startsWith("**", i)) {
                append(text[i])
                i++
            } else if (end < 0) {
                // An unclosed marker is shown as the author typed it.
                append(text.substring(i))
                i = text.length
            } else {
                val bold = text.startsWith("**", i)
                val inner = text.substring(i + if (bold) 2 else 1, end)
                pushStyle(
                    if (bold) SpanStyle(fontWeight = FontWeight.SemiBold) else SpanStyle(fontFamily = FontFamily.Monospace),
                )
                append(inner)
                pop()
                i = end + if (bold) 2 else 1
            }
        }
    }

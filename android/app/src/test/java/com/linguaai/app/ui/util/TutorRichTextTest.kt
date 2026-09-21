package com.linguaai.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sentence-correction prompt (server `PromptBuilder.modeInstruction`)
 * requires the model to answer in markdown, so the parser is specified against
 * that exact shape rather than against markdown in general.
 */
class TutorRichTextTest {
    @Test
    fun `bold markers become a span instead of literal asterisks`() {
        val blocks = parseTutorMarkdown("**Corrected:** 病気だったので、学校に行きませんでした。")

        val line = blocks.single() as TutorBlock.Line
        assertEquals("Corrected: 病気だったので、学校に行きませんでした。", line.text.text)
        assertEquals(
            listOf("Corrected:"),
            line.text.spanStyles
                .filter { it.item.fontWeight != null }
                .map { line.text.text.substring(it.start, it.end) },
        )
    }

    @Test
    fun `bullet lines become bullets and plain lines stay paragraphs`() {
        val blocks =
            parseTutorMarkdown(
                """
                |What changed:
                |- replaced から with ので
                |  because the reason precedes the result
                """.trimMargin(),
            )

        assertTrue(blocks[0] is TutorBlock.Line)
        assertTrue(blocks[1] is TutorBlock.Bullet)
        assertEquals("replaced から with ので", (blocks[1] as TutorBlock.Bullet).text.text)
        assertTrue(blocks[2] is TutorBlock.Line)
    }

    /**
     * The correction reply is shaped as "**Corrected:** …" then a blank line
     * then "**What changed:** …". Dropping the blank line collapsed that into
     * one unreadable block.
     */
    @Test
    fun `a blank line becomes a paragraph break instead of vanishing`() {
        val blocks =
            parseTutorMarkdown(
                """
                |First paragraph.
                |
                |Second paragraph.
                """.trimMargin(),
            )

        assertEquals(3, blocks.size)
        assertEquals(TutorBlock.Blank, blocks[1])
    }

    @Test
    fun `trailing blank lines do not add empty spacing blocks`() {
        val blocks =
            parseTutorMarkdown(
                """
                |Only text.
                |
                """.trimMargin(),
            )

        assertEquals(1, blocks.size)
    }

    @Test
    fun `fenced code becomes a code block and keeps its contents verbatim`() {
        val blocks = parseTutorMarkdown("Try this:\n```\n私はパンを食べます\n```")

        assertEquals("私はパンを食べます", (blocks.last() as TutorBlock.Code).text)
    }

    @Test
    fun `an unterminated fence does not swallow the reply`() {
        val blocks = parseTutorMarkdown("answer\n```\ntruncated tail")

        assertEquals(2, blocks.size)
        assertEquals("truncated tail", (blocks[1] as TutorBlock.Code).text)
    }

    @Test
    fun `inline code spans are marked monospace`() {
        val line =
            parseTutorMarkdown("Use ので/から after a plain reason `clause`.")
                .single() as TutorBlock.Line

        assertEquals("Use ので/から after a plain reason clause.", line.text.text)
        assertTrue(line.text.spanStyles.isNotEmpty())
    }

    @Test
    fun `spoken text drops the markers the learner never sees`() {
        val spoken =
            tutorPlainText(
                "**Corrected:** 行きませんでした。\n\n" +
                    "- marked the topic\n\n" +
                    "Try `ので`.",
            )

        assertEquals("Corrected: 行きませんでした。\nmarked the topic\nTry ので.", spoken)
    }

    @Test
    fun `spoken text keeps a fenced block's content`() {
        val spoken = tutorPlainText("before\n```\n私はパンを食べます\n```\nafter")

        assertTrue(spoken.contains("私はパンを食べます"))
        assertFalse("the fence itself is not speech", spoken.contains("```"))
    }
}

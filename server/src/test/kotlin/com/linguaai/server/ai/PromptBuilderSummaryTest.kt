package com.linguaai.server.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rolling-summary folding.
 *
 * The cap is load-bearing: the summary is what keeps context cost flat for a long
 * conversation. Without it the summary is appended to on every eviction and grows
 * without bound, eventually costing more tokens than the messages it replaced.
 */
class PromptBuilderSummaryTest {

    private val cap = PromptBuilder.MAX_SUMMARY_CHARS

    @Test
    fun `a first addition with no existing summary is used as-is`() {
        assertEquals("Learner: hello", PromptBuilder.foldSummary(null, "Learner: hello"))
        assertEquals("Learner: hello", PromptBuilder.foldSummary("", "Learner: hello"))
    }

    @Test
    fun `additions are appended below the existing summary`() {
        val result = PromptBuilder.foldSummary("older", "newer")

        assertEquals("older\nnewer", result)
    }

    @Test
    fun `a blank addition leaves the existing summary untouched`() {
        assertEquals("older", PromptBuilder.foldSummary("older", ""))
        assertEquals("older", PromptBuilder.foldSummary("older", "   "))
    }

    @Test
    fun `the summary never exceeds the cap`() {
        var summary: String? = null
        repeat(50) { round ->
            summary = PromptBuilder.foldSummary(
                summary,
                (1..10).joinToString("\n") { "Learner: message $round-$it" },
            )
        }

        assertTrue(
            summary!!.length <= cap,
            "summary grew past the cap: ${summary.length} > $cap",
        )
    }

    @Test
    fun `trimming keeps the newest content and drops the oldest`() {
        val old = "Learner: the very first thing ever said"
        val filler = (1..400).joinToString("\n") { "Learner: filler $it" }

        val result = PromptBuilder.foldSummary(old, filler)

        assertTrue(result.length <= cap, "expected trimming to apply")
        // The newest material must survive; the oldest is what gets dropped.
        assertTrue(result.contains("filler 400"), "newest content was lost")
        assertTrue(!result.contains("very first thing"), "oldest content should be dropped first")
    }

    @Test
    fun `trimming does not start the summary mid-line`() {
        // Build a summary whose cap boundary would fall inside a line.
        val existing = "x".repeat(cap - 10)
        val result = PromptBuilder.foldSummary(existing, "Learner: a\nTutor: b\nLearner: c")

        assertTrue(result.length <= cap)
        // Whatever survives must begin at a line boundary, not mid-word.
        assertTrue(
            result.startsWith("Learner:") || result.startsWith("Tutor:"),
            "summary starts mid-line: '${result.take(40)}'",
        )
    }
}

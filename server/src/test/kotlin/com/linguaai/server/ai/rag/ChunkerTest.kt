package com.linguaai.server.ai.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkerTest {
    private val chunker = Chunker()

    @Test
    fun `short text stays one chunk`() {
        val text = "これは一文です。これはもう一文です。"
        val chunks = chunker.chunk(text)
        assertEquals(listOf(text), chunks)
    }

    @Test
    fun `long text splits on sentence boundaries within the budget`() {
        val sentence = "The quick brown fox jumps over the lazy dog near the river bank. "
        val text = sentence.repeat(40)
        val chunks = chunker.chunk(text)
        assertTrue(chunks.size > 1, "long text must split")
        assertTrue(chunks.all { it.length <= 600 }, "no chunk may exceed the budget")
        val rejoined = chunks.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        val original = text.replace(Regex("\\s+"), " ").trim()
        assertEquals(original.length, rejoined.length, "no content may be lost")
    }

    @Test
    fun `a single oversized sentence is hard-wrapped`() {
        val text = "a".repeat(1500)
        val chunks = chunker.chunk(text)
        assertTrue(chunks.size >= 3)
        assertTrue(chunks.all { it.length <= 600 })
    }

    @Test
    fun `a tiny trailing fragment merges into the previous chunk`() {
        val longPart = "Meaningful sentence one. ".repeat(80)
        val text = longPart + "End."
        val chunks = chunker.chunk(text)
        assertTrue(chunks.last().endsWith("End."))
    }

    @Test
    fun `blank text yields no chunks`() {
        assertTrue(chunker.chunk("   \n  ").isEmpty())
    }
}

package com.linguaai.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ListenAndTypeRoundTest {
    @Test
    fun `answer matching trims whitespace and ignores case`() {
        assertTrue(ListenAndTypeRound.answersMatch("Listen", "  lIsTeN  "))
    }

    @Test
    fun `answer matching treats canonically equivalent unicode as equal`() {
        assertTrue(ListenAndTypeRound.answersMatch("café", "cafe\u0301"))
    }

    @Test
    fun `answer matching preserves accents`() {
        assertFalse(ListenAndTypeRound.answersMatch("café", "cafe"))
    }

    @Test
    fun `empty answer does not match`() {
        assertFalse(ListenAndTypeRound.answersMatch("word", "   "))
    }

    @Test
    fun `round filters other languages duplicate ids and duplicate headwords`() {
        val candidates =
            listOf(
                card(id = 1, word = "café"),
                card(id = 1, word = "duplicate id"),
                card(id = 2, word = "cafe\u0301"),
                card(id = 3, word = "wrong language", languageId = 2),
                card(id = 4, word = "hello"),
            )

        val round = ListenAndTypeRound.start(languageId = 1, candidates, Random(7))

        assertEquals(setOf(1L, 4L), round.words.map { it.id }.toSet())
        assertTrue(round.words.all { it.languageId == 1L })
    }

    @Test
    fun `round is empty when no matching language candidates exist`() {
        val round = ListenAndTypeRound.start(languageId = 1, listOf(card(id = 1, word = "hello", languageId = 2)))

        assertTrue(round.isEmpty)
        assertTrue(round.isComplete)
        assertNull(round.currentWord)
    }

    @Test
    fun `round handles one eligible prompt`() {
        val onlyWord = card(id = 1, word = "hello")

        val round = ListenAndTypeRound.start(languageId = 1, listOf(onlyWord), Random(7))

        assertEquals(listOf(onlyWord), round.words)
        assertSame(onlyWord, round.currentWord)
        assertFalse(round.isComplete)
    }

    @Test
    fun `round takes at most ten unique headwords`() {
        val candidates = (1L..15L).map { card(id = it, word = "word-$it") }

        val round = ListenAndTypeRound.start(languageId = 1, candidates, Random(7))

        assertEquals(ListenAndTypeRound.MAX_WORDS, round.words.size)
        assertEquals(
            round.words.size,
            round.words
                .map { it.id }
                .toSet()
                .size,
        )
        assertEquals(
            round.words.size,
            round.words
                .map { it.word }
                .toSet()
                .size,
        )
    }

    @Test
    fun `answer can be submitted only once before advancing`() {
        val round = ListenAndTypeRound.start(languageId = 1, listOf(card(id = 1, word = "hello")))

        val submitted = round.submitAnswer("HELLO")
        val repeated = submitted.submitAnswer("wrong")

        assertEquals(true, submitted.answeredCorrectly)
        assertEquals(1, submitted.correctCount)
        assertSame(submitted, repeated)
        assertTrue(submitted.advance().isComplete)
    }

    @Test
    fun `retry resets score and returns to the first prompt`() {
        val round =
            ListenAndTypeRound.start(
                languageId = 1,
                candidates = listOf(card(id = 1, word = "first"), card(id = 2, word = "second")),
                random = Random(7),
            )
        val completed =
            round
                .submitAnswer(round.currentWord!!.word)
                .advance()
                .submitAnswer("wrong")
                .advance()

        val retried = completed.retry(Random(9))

        assertEquals(0, retried.currentIndex)
        assertEquals(0, retried.correctCount)
        assertNull(retried.answeredCorrectly)
        assertEquals(round.words.map { it.id }.toSet(), retried.words.map { it.id }.toSet())
    }

    private fun card(
        id: Long,
        word: String,
        languageId: Long = 1,
    ): VocabularyCard =
        VocabularyCard(
            id = id,
            languageId = languageId,
            level = "N3",
            word = word,
            reading = null,
            pronunciation = null,
            meaning = "meaning $word",
            example = null,
            exampleTranslation = null,
            category = null,
            favorite = false,
            masteryLevel = 0,
        )
}

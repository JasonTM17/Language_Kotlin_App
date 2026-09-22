package com.linguaai.app.ui.screens.home

import com.linguaai.app.domain.model.VocabularyCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WordOfTheDayTest {
    private fun word(id: Long): VocabularyCard =
        VocabularyCard(
            id = id,
            languageId = 1,
            level = "N3",
            word = "w$id",
            reading = null,
            pronunciation = null,
            meaning = "meaning $id",
            example = null,
            exampleTranslation = null,
            category = null,
            favorite = false,
            masteryLevel = 0,
        )

    @Test
    fun `empty tracked list yields no word`() {
        assertNull(wordOfTheDay(emptyList(), LocalDate.of(2026, 9, 22)))
    }

    @Test
    fun `same date yields the same word across calls`() {
        val words = (1L..50L).map(::word)
        val date = LocalDate.of(2026, 9, 22)
        assertEquals(wordOfTheDay(words, date), wordOfTheDay(words, date))
    }

    @Test
    fun `next date moves to the next slot in rotation`() {
        val words = (1L..10L).map(::word)
        val first = wordOfTheDay(words, LocalDate.of(2026, 9, 22))
        val second = wordOfTheDay(words, LocalDate.of(2026, 9, 23))
        assertEquals(words[(words.indexOf(first) + 1) % words.size], second)
    }

    @Test
    fun `selection wraps around the list`() {
        val words = (1L..3L).map(::word)
        assertEquals(words[0], wordOfTheDay(words, LocalDate.of(2026, 1, 1)))
        assertEquals(words[1], wordOfTheDay(words, LocalDate.of(2026, 1, 2)))
        assertEquals(words[2], wordOfTheDay(words, LocalDate.of(2026, 1, 3)))
        assertEquals(words[0], wordOfTheDay(words, LocalDate.of(2026, 1, 4)))
    }
}

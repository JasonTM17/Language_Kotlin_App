package com.linguaai.app.ui.navigation

import com.linguaai.app.domain.model.DailyQuestType
import com.linguaai.app.domain.model.VocabularyCard
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyQuestNavigationTest {
    @Test
    fun `each quest opens its existing typed destination`() {
        val word = vocabularyWord()

        assertEquals(AiChatRoute(mode = "general"), dailyQuestDestination(DailyQuestType.AI_CHAT, null))
        assertEquals(FlashcardRoute, dailyQuestDestination(DailyQuestType.FLASHCARDS, null))
        assertEquals(LearnRoute, dailyQuestDestination(DailyQuestType.QUIZ, null))
        assertEquals(
            AiChatRoute(mode = "general", seed = wordOfDayAiSeed(word)),
            dailyQuestDestination(DailyQuestType.WORD_OF_DAY, word),
        )
    }

    @Test
    fun `word of the day without a current word falls back to vocabulary`() {
        assertEquals(VocabularyRoute, dailyQuestDestination(DailyQuestType.WORD_OF_DAY, null))
    }

    @Test
    fun `word of the day seed uses the current word and meaning`() {
        assertEquals(
            "How do I naturally use the word 'hello' (greeting) in conversation? Give me 2 example sentences.",
            wordOfDayAiSeed(vocabularyWord()),
        )
    }

    private fun vocabularyWord() =
        VocabularyCard(
            id = 1,
            languageId = 1,
            level = "A1",
            word = "hello",
            reading = null,
            pronunciation = null,
            meaning = "greeting",
            example = null,
            exampleTranslation = null,
            category = "Daily",
            favorite = false,
            masteryLevel = 0,
        )
}

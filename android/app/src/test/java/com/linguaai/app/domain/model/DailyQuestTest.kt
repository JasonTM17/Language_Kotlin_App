package com.linguaai.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyQuestTest {
    @Test
    fun `quest progress fraction calculates correctly`() {
        val quest =
            DailyQuest(
                type = DailyQuestType.FLASHCARDS,
                progress = 5,
                isClaimed = false,
            )
        assertEquals(0.5f, quest.progressFraction, 0.001f)
        assertFalse(quest.isCompleted)
    }

    @Test
    fun `completed quest caps fraction at 1 and flags completed`() {
        val quest =
            DailyQuest(
                type = DailyQuestType.AI_CHAT,
                progress = 1,
                isClaimed = false,
            )
        assertEquals(1.0f, quest.progressFraction, 0.001f)
        assertTrue(quest.isCompleted)
    }

    @Test
    fun `over achieved quest still caps progress fraction at 1`() {
        val quest =
            DailyQuest(
                type = DailyQuestType.QUIZ,
                progress = 5,
                isClaimed = true,
            )
        assertEquals(1.0f, quest.progressFraction, 0.001f)
        assertTrue(quest.isCompleted)
        assertTrue(quest.isClaimed)
    }
}

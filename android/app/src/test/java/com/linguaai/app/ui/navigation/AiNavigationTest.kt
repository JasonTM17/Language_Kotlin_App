package com.linguaai.app.ui.navigation

import com.linguaai.app.data.remote.dto.AiSourceDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiNavigationTest {
    @Test
    fun `new chatbot tools preserve their requested mode`() {
        assertEquals("general", chatRouteMode(null, "general"))
        assertEquals("conversation-practice", chatRouteMode(null, "conversation-practice"))
        assertEquals("sentence-correction", chatRouteMode(null, "sentence-correction"))
    }

    @Test
    fun `specialized history preserves routing while generic history uses conversation mode`() {
        assertEquals("conversation-practice", chatRouteMode(7, "conversation-practice"))
        assertEquals("sentence-correction", chatRouteMode(8, "sentence-correction"))
        assertEquals("conversation", chatRouteMode(9, "general"))
        assertEquals("conversation", chatRouteMode(10, "grammar-explain"))
    }

    /**
     * The chip is only trustworthy if the id it carries is the id the target
     * screen reads. Retrieval tags LESSON hits with `Lessons.id` and GRAMMAR
     * hits with `GrammarLessons.id`, which is exactly what these routes take.
     */
    @Test
    fun `lesson and grammar citations route to their detail screen with the cited id`() {
        assertEquals(
            LessonDetailRoute(42L),
            sourceRouteFor(source("LESSON", 42L)),
        )
        assertEquals(
            GrammarDetailRoute(7L),
            sourceRouteFor(source("GRAMMAR", 7L)),
        )
    }

    @Test
    fun `vocabulary citations have no destination and must not navigate`() {
        assertNull(sourceRouteFor(source("VOCABULARY", 9L)))
        assertNull(sourceRouteFor(source("UNKNOWN_KIND", 9L)))
    }

    private fun source(
        sourceType: String,
        sourceId: Long,
    ) = AiSourceDto(
        title = "cited",
        sourceType = sourceType,
        sourceId = sourceId,
        chunkIndex = 0,
        level = "N4",
        score = 0.9,
    )
}

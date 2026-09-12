package com.linguaai.app.ui.navigation

import org.junit.Assert.assertEquals
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
}

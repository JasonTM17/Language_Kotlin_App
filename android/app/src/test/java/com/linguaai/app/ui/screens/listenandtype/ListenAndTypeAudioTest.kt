package com.linguaai.app.ui.screens.listenandtype

import com.linguaai.app.domain.model.VocabularyCard
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenAndTypeAudioTest {
    @Test
    fun `japanese playback uses the headword when reading is blank`() {
        assertEquals("環境", listenAndTypeFallbackText(card(reading = ""), "ja"))
    }

    @Test
    fun `japanese playback uses a nonblank reading`() {
        assertEquals("かんきょう", listenAndTypeFallbackText(card(reading = "かんきょう"), "ja"))
    }

    @Test
    fun `other languages ignore the Japanese reading field`() {
        assertEquals("環境", listenAndTypeFallbackText(card(reading = "かんきょう"), "en"))
    }

    private fun card(reading: String): VocabularyCard =
        VocabularyCard(
            id = 1,
            languageId = 1,
            level = "N3",
            word = "環境",
            reading = reading,
            pronunciation = null,
            meaning = "environment",
            example = null,
            exampleTranslation = null,
            category = null,
            favorite = false,
            masteryLevel = 0,
        )
}

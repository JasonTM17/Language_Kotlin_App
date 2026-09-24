package com.linguaai.app.data.remote.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WiktionaryAudioRepositoryAndroidTest {
    @Test
    fun languageSectionRegexesInitializeOnAndroid() {
        val wikitext =
            """
            ==English==
            {{audio|en|En-uk-a cat.ogg|a=RP|text=a cat}}
            {{audio|en|En-us-cat.ogg|a=GA}}
            ==Japanese==
            {{ja-pron|ねこ}}
            """.trimIndent()

        assertEquals("En-us-cat.ogg", WiktionaryAudioRepository.extractAudioFile(wikitext, "en", "cat"))
        assertEquals(null, WiktionaryAudioRepository.extractAudioFile(wikitext, "ja"))
    }
}

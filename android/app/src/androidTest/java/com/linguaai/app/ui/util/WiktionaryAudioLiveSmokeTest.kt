package com.linguaai.app.ui.util

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.linguaai.app.data.remote.audio.WiktionaryAudioRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class WiktionaryAudioLiveSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liveRecordingsPlayWithCreditsAndMissingJapaneseAudioFallsBack() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveAudioSmoke") == "true")

        val repository = AtomicReference<WiktionaryAudioRepository>()
        val ttsRef = AtomicReference<LinguaTts>()
        val japaneseFallback = AtomicReference<SpeechRequestResult>()
        val replacementSpeech = AtomicReference<SpeechRequestResult>()
        val stopRequests = AtomicInteger()
        composeRule.setContent {
            val audioRepository = rememberWiktionaryAudioRepository()
            val tts = rememberLinguaTts("en")
            androidx.compose.runtime.SideEffect {
                repository.set(audioRepository)
                ttsRef.set(tts)
                tts.onSpeechRequestHandled = { request ->
                    if (request.locale == Locale.JAPANESE && request.text == "かんきょう") japaneseFallback.set(request)
                    if (request.text == "に") replacementSpeech.set(request)
                }
                tts.onSpeechStopRequested = { stopRequests.incrementAndGet() }
            }

            Column {
                Button(onClick = { tts.speak("cat", "en") }) { Text("Speak English cat") }
                Button(onClick = { tts.speak("学生", "ja", "がくせい") }) { Text("Speak Japanese student") }
                Button(onClick = { tts.speak("環境", "ja", "かんきょう") }) { Text("Speak Japanese environment") }
                Button(onClick = { tts.speak("二", null, "に") }) { Text("Speak replacement Japanese TTS") }
            }
        }
        composeRule.runOnIdle { check(repository.get() != null) }

        composeRule.onNodeWithText("Speak English cat").performClick()
        waitForAttribution()
        composeRule.onNodeWithText("En-us-cat.ogg", substring = true).assertIsDisplayed()
        assertCreditLinks()
        composeRule.onNodeWithText("Done").performClick()

        composeRule.onNodeWithText("Speak Japanese student").performClick()
        waitForAttribution()
        composeRule.onNodeWithText("Ja-gakusei-anglonative.oga", substring = true).assertIsDisplayed()
        assertCreditLinks()
        composeRule.onNodeWithText("Done").performClick()

        composeRule.onNodeWithText("Speak Japanese environment").performClick()
        val missingJapaneseAudio =
            runBlocking {
                withTimeout(45_000) {
                    repository.get()!!.findPronunciation("環境", "ja")
                }
            }
        assertNull(missingJapaneseAudio)
        composeRule.waitUntil(timeoutMillis = 30_000) { japaneseFallback.get() != null }
        composeRule.runOnIdle {
            val request = checkNotNull(japaneseFallback.get())
            check(request.text == "かんきょう")
            check(request.locale == Locale.JAPANESE)
            check(request.accepted) { "Japanese TTS engine did not accept the fallback utterance" }
        }
        check(ttsRef.get() != null)
        composeRule.onAllNodesWithText("Pronunciation source").assertCountEquals(0)

        stopRequests.set(0)
        composeRule.onNodeWithText("Speak replacement Japanese TTS").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) { stopRequests.get() > 0 && replacementSpeech.get() != null }
        composeRule.runOnIdle {
            val request = checkNotNull(replacementSpeech.get())
            check(request.text == "に")
            check(request.locale == Locale.JAPANESE)
            check(request.accepted) { "Japanese TTS engine did not accept the replacement utterance" }
        }
    }

    private fun waitForAttribution() {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText("Pronunciation source").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertCreditLinks() {
        composeRule.onNodeWithText("Wiktionary entry").assertIsDisplayed()
        composeRule.onNodeWithText("Audio file and credit").assertIsDisplayed()
        composeRule.onNodeWithText("License terms").assertIsDisplayed()
    }
}

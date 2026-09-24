package com.linguaai.app.ui.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LinguaTtsTest {
    @Test
    fun `localeFromCode maps standard codes properly`() {
        assertEquals(Locale.JAPANESE, LinguaTts.localeFromCode("ja"))
        assertEquals(Locale.ENGLISH, LinguaTts.localeFromCode("en"))
        assertEquals(Locale.FRENCH, LinguaTts.localeFromCode("fr"))
        assertEquals(Locale.GERMAN, LinguaTts.localeFromCode("de"))
        assertEquals(Locale.KOREAN, LinguaTts.localeFromCode("ko"))
        assertEquals(Locale("vi", "VN"), LinguaTts.localeFromCode("vi"))
    }

    @Test
    fun `resolveLocale auto detects Japanese characters`() {
        val locale = LinguaTts.resolveLocale("こんにちは", null)
        assertEquals(Locale.JAPANESE, locale)
    }

    @Test
    fun `resolveLocale auto detects Vietnamese tones`() {
        val locale = LinguaTts.resolveLocale("xin chào", null)
        assertEquals(Locale("vi", "VN"), locale)
    }

    @Test
    fun `resolveLocale respects explicit language code override`() {
        val locale = LinguaTts.resolveLocale("bonjour", "fr")
        assertEquals(Locale.FRENCH, locale)
    }

    @Test
    fun `resolveLocale uses Japanese code for kanji and reads supplied kana`() {
        assertEquals(Locale.JAPANESE, LinguaTts.resolveLocale("環境", "ja"))
        assertEquals(Locale.JAPANESE, LinguaTts.resolveLocale("かんきょう", "ja"))
    }

    @Test
    fun `localeFromCode covers every catalogue language`() {
        val codes = listOf("ar", "de", "en", "es", "fr", "hi", "id", "it", "ja", "ko", "nl", "pt", "ru", "th", "tr", "vi", "zh")

        codes.forEach { code -> assertEquals(code, LinguaTts.localeFromCode(code).language) }
    }

    @Test
    fun `canceling an older lookup cannot enqueue its word after the newer word`() =
        runBlocking {
            val lookupStarted = CompletableDeferred<Unit>()
            val fallbackRequests = mutableListOf<String>()
            val oldWord =
                launch {
                    val recording =
                        lookupPronunciationOrNull {
                            lookupStarted.complete(Unit)
                            awaitCancellation()
                        }
                    if (recording == null) fallbackRequests += "word-a"
                }

            lookupStarted.await()
            oldWord.cancelAndJoin()
            val currentWord = lookupPronunciationOrNull { null }
            if (currentWord == null) fallbackRequests += "word-b"

            assertEquals(listOf("word-b"), fallbackRequests)
        }

    @Test
    fun `lookup cancellation stays canceled instead of becoming a missing audio result`() =
        runBlocking {
            val lookupStarted = CompletableDeferred<Unit>()
            val lookup =
                async {
                    lookupPronunciationOrNull {
                        lookupStarted.complete(Unit)
                        awaitCancellation()
                    }
                }

            lookupStarted.await()
            lookup.cancel()
            try {
                lookup.await()
                throw AssertionError("Canceled lookup unexpectedly returned a pronunciation miss")
            } catch (_: CancellationException) {
                assertTrue(lookup.isCancelled)
            }
        }

    @Test
    fun `ordinary pronunciation provider failures remain eligible for TTS fallback`() =
        runBlocking {
            val result = lookupPronunciationOrNull { throw IllegalStateException("offline") }

            assertNull(result)
        }

    @Test
    fun `late error from replaced recording cannot start its old word fallback`() {
        val previousPlayer = Any()
        val currentPlayer = Any()
        val actions = mutableListOf<String>()

        handlePlaybackError(
            currentPlayer,
            previousPlayer,
            release = { actions += "release-old" },
            fallback = { actions += "fallback-old" },
        )

        assertEquals(listOf("release-old"), actions)

        handlePlaybackError(
            currentPlayer,
            currentPlayer,
            release = { actions += "release-current" },
            fallback = { actions += "fallback-current" },
        )
        assertEquals(listOf("release-old", "release-current", "fallback-current"), actions)
    }
}

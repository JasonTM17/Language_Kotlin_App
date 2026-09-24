package com.linguaai.app.ui.util

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Lightweight Text-to-Speech manager for native pronunciation playback across
 * vocabulary, flashcards, word-of-the-day, and AI tutor dialogue.
 */
class LinguaTts(
    context: Context,
    private val preferredLanguageCode: String? = null,
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady: Boolean = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            preferredLanguageCode?.let { setLanguageByCode(it) }
        }
    }

    /** Speak the provided text using language-aware locale resolution. */
    fun speak(
        text: String,
        languageCode: String? = preferredLanguageCode,
    ) {
        val engine = tts ?: return
        if (!isReady || text.isBlank()) return

        val targetLocale = resolveLocale(text, languageCode)
        runCatching {
            engine.language = targetLocale
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
        }
    }

    private fun setLanguageByCode(code: String) {
        val locale = localeFromCode(code)
        runCatching { tts?.language = locale }
    }

    companion object {
        fun resolveLocale(
            text: String,
            languageCode: String?,
        ): Locale {
            if (!languageCode.isNullOrBlank()) {
                return localeFromCode(languageCode)
            }
            // Auto-detect Japanese scripts (Hiragana, Katakana, CJK Ideographs)
            if (text.any { c -> c in '\u3040'..'\u309F' || c in '\u30A0'..'\u30FF' }) {
                return Locale.JAPANESE
            }
            // Auto-detect Korean Hangul
            if (text.any { c -> c in '\uAC00'..'\uD7AF' }) {
                return Locale.KOREAN
            }
            // Auto-detect Vietnamese tone marks
            val viMarks = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ"
            if (text.any { c -> viMarks.contains(c, ignoreCase = true) }) {
                return Locale("vi", "VN")
            }
            return Locale.getDefault()
        }

        fun localeFromCode(code: String): Locale =
            when (code.lowercase().trim()) {
                "ja", "jpn", "japanese" -> Locale.JAPANESE
                "en", "eng", "english" -> Locale.ENGLISH
                "fr", "fra", "french" -> Locale.FRENCH
                "de", "deu", "german" -> Locale.GERMAN
                "vi", "vie", "vietnamese" -> Locale("vi", "VN")
                "es", "spa", "spanish" -> Locale("es", "ES")
                "zh", "zho", "chinese" -> Locale.CHINESE
                "ko", "kor", "korean" -> Locale.KOREAN
                "it", "ita", "italian" -> Locale.ITALIAN
                else -> Locale.forLanguageTag(code)
            }
    }
}

/**
 * Remember and lifecycle-bind a [LinguaTts] instance for use in Composable screens.
 */
@Composable
fun rememberLinguaTts(languageCode: String? = null): LinguaTts {
    val context = LocalContext.current
    val tts = remember(languageCode) { LinguaTts(context, languageCode) }

    DisposableEffect(tts) {
        onDispose {
            tts.shutdown()
        }
    }
    return tts
}

package com.linguaai.app.ui.util

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.linguaai.app.R
import com.linguaai.app.data.remote.audio.WikimediaPronunciation
import com.linguaai.app.data.remote.audio.WiktionaryAudioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Language-aware pronunciation, preferring licensed recordings when indexed. */
class LinguaTts(
    context: Context,
    private val preferredLanguageCode: String? = null,
    private val audioRepository: WiktionaryAudioRepository,
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var mediaPlayer: MediaPlayer? = null
    private var pronunciationJob: Job? = null
    private var pendingUtterance: PendingUtterance? = null

    @Volatile private var isReady = false
    private val _audioCredit = MutableStateFlow<WikimediaPronunciation?>(null)
    val audioCredit: StateFlow<WikimediaPronunciation?> = _audioCredit.asStateFlow()
    internal var onSpeechRequestHandled: ((SpeechRequestResult) -> Unit)? = null
    internal var onSpeechStopRequested: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            pendingUtterance = null
            showVoiceUnavailable()
            return
        }
        isReady = true
        pendingUtterance?.let { pending ->
            pendingUtterance = null
            scope.launch { speakWithLocale(pending.text, pending.locale) }
        }
    }

    /** Speak a catalog headword, using [fallbackText] for script-specific readings such as Japanese kana. */
    fun speak(
        text: String,
        languageCode: String? = preferredLanguageCode,
        fallbackText: String = text,
    ) {
        if (text.isBlank() || fallbackText.isBlank()) return
        pronunciationJob?.cancel()
        stopPlayback()
        stopTtsSpeech()
        pendingUtterance = null
        _audioCredit.value = null
        val locale = resolveLocale(fallbackText, languageCode)
        pronunciationJob =
            scope.launch {
                val recording =
                    lookupPronunciationOrNull {
                        languageCode
                            ?.takeIf(String::isNotBlank)
                            ?.let { code -> audioRepository.findPronunciation(text, code) }
                    }
                if (recording == null) {
                    speakWithLocale(fallbackText, locale)
                } else {
                    playRecording(recording, fallbackText, locale)
                }
            }
    }

    fun dismissAudioCredit() {
        _audioCredit.value = null
    }

    fun stop() {
        pronunciationJob?.cancel()
        pronunciationJob = null
        pendingUtterance = null
        stopPlayback()
        stopTtsSpeech()
    }

    fun shutdown() {
        stop()
        scope.cancel()
        runCatching { tts?.shutdown() }
        tts = null
        isReady = false
    }

    private fun playRecording(
        recording: WikimediaPronunciation,
        fallbackText: String,
        locale: Locale,
    ) {
        val player = MediaPlayer()
        mediaPlayer = player
        player.setAudioAttributes(
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        player.setOnPreparedListener { prepared ->
            if (mediaPlayer !== prepared) {
                runCatching { prepared.release() }
                return@setOnPreparedListener
            }
            runCatching {
                prepared.start()
                _audioCredit.value = recording
            }.onFailure {
                releasePlayer(prepared)
                speakWithLocale(fallbackText, locale)
            }
        }
        player.setOnCompletionListener { completed -> releasePlayer(completed) }
        player.setOnErrorListener { failed, _, _ ->
            handlePlaybackError(
                currentPlayer = mediaPlayer,
                callbackPlayer = failed,
                release = { releasePlayer(failed) },
                fallback = { speakWithLocale(fallbackText, locale) },
            )
            true
        }
        runCatching {
            player.setDataSource(recording.audioUrl)
            player.prepareAsync()
        }.onFailure {
            releasePlayer(player)
            speakWithLocale(fallbackText, locale)
        }
    }

    private fun speakWithLocale(
        text: String,
        locale: Locale,
    ) {
        val engine = tts ?: return
        if (!isReady) {
            pendingUtterance = PendingUtterance(text, locale)
            return
        }
        val available = runCatching { engine.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (available < TextToSpeech.LANG_AVAILABLE) {
            recordSpeechRequest(text, locale, accepted = false)
            showVoiceUnavailable()
            return
        }
        val selected = runCatching { engine.setLanguage(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (selected < TextToSpeech.LANG_AVAILABLE) {
            recordSpeechRequest(text, locale, accepted = false)
            showVoiceUnavailable()
            return
        }
        runCatching {
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "linguaai_${System.currentTimeMillis()}")
            recordSpeechRequest(text, locale, accepted = result != TextToSpeech.ERROR)
            if (result == TextToSpeech.ERROR) showVoiceUnavailable()
        }.onFailure {
            recordSpeechRequest(text, locale, accepted = false)
            showVoiceUnavailable()
        }
    }

    private fun recordSpeechRequest(
        text: String,
        locale: Locale,
        accepted: Boolean,
    ) {
        runCatching { onSpeechRequestHandled?.invoke(SpeechRequestResult(text, locale, accepted)) }
    }

    private fun showVoiceUnavailable() {
        Toast.makeText(appContext, R.string.tts_voice_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun releasePlayer(player: MediaPlayer) {
        if (mediaPlayer === player) mediaPlayer = null
        runCatching {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.release()
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            releasePlayer(player)
        }
    }

    private fun stopTtsSpeech() {
        val engine = tts ?: return
        runCatching { engine.stop() }
        runCatching { onSpeechStopRequested?.invoke() }
    }

    private data class PendingUtterance(
        val text: String,
        val locale: Locale,
    )

    companion object {
        fun resolveLocale(
            text: String,
            languageCode: String?,
        ): Locale {
            if (!languageCode.isNullOrBlank()) return localeFromCode(languageCode)
            if (text.any { c -> c in '\u3040'..'\u309F' || c in '\u30A0'..'\u30FF' }) return Locale.JAPANESE
            if (text.any { c -> c in '\uAC00'..'\uD7AF' }) return Locale.KOREAN
            val viMarks = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểệìíỉịòóõọôồốổỗộơờớởỡợùúủụưừứửữựỳýỷỹỵđ"
            if (text.any { c -> viMarks.contains(c, ignoreCase = true) }) return Locale("vi", "VN")
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

internal data class SpeechRequestResult(
    val text: String,
    val locale: Locale,
    val accepted: Boolean,
)

internal suspend fun lookupPronunciationOrNull(lookup: suspend () -> WikimediaPronunciation?): WikimediaPronunciation? =
    try {
        lookup()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

internal fun handlePlaybackError(
    currentPlayer: Any?,
    callbackPlayer: Any,
    release: () -> Unit,
    fallback: () -> Unit,
) {
    val isCurrent = currentPlayer === callbackPlayer
    release()
    if (isCurrent) fallback()
}

/** Remember and lifecycle-bind a [LinguaTts], including attribution for streamed Commons audio. */
@Composable
fun rememberLinguaTts(languageCode: String? = null): LinguaTts {
    val context = LocalContext.current
    val repository = rememberWiktionaryAudioRepository()
    val tts = remember(languageCode, repository) { LinguaTts(context, languageCode, repository) }

    DisposableEffect(tts) {
        onDispose { tts.shutdown() }
    }

    val credit by tts.audioCredit.collectAsState()
    credit?.let { attribution ->
        AudioAttributionDialog(
            attribution = attribution,
            onDismiss = tts::dismissAudioCredit,
        )
    }
    return tts
}

@Composable
private fun AudioAttributionDialog(
    attribution: WikimediaPronunciation,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                androidx.compose.ui.res
                    .stringResource(R.string.audio_attribution_title),
            )
        },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    androidx.compose.ui.res.stringResource(
                        R.string.audio_attribution_details,
                        attribution.fileName,
                        attribution.author,
                        attribution.licenseName,
                    ),
                )
                TextButton(onClick = { openLink(context, attribution.sourcePageUrl) }) {
                    Text(
                        androidx.compose.ui.res
                            .stringResource(R.string.audio_open_wiktionary),
                    )
                }
                TextButton(onClick = { openLink(context, attribution.filePageUrl) }) {
                    Text(
                        androidx.compose.ui.res
                            .stringResource(R.string.audio_open_commons_file),
                    )
                }
                TextButton(onClick = { openLink(context, attribution.licenseUrl) }) {
                    Text(
                        androidx.compose.ui.res
                            .stringResource(R.string.audio_open_license),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    androidx.compose.ui.res
                        .stringResource(R.string.common_done),
                )
            }
        },
    )
}

private fun openLink(
    context: Context,
    url: String,
) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

package com.linguaai.app.data.remote.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import java.util.LinkedHashMap
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** A verified, remotely hosted Commons pronunciation and its required credit links. */
data class WikimediaPronunciation(
    val audioUrl: String,
    val fileName: String,
    val filePageUrl: String,
    val sourcePageUrl: String,
    val author: String,
    val licenseName: String,
    val licenseUrl: String,
)

/**
 * Best-effort, on-demand pronunciation lookup. The headword is matched only
 * inside its English Wiktionary language section; a Commons file is returned
 * only when its URL, MIME type, license, and attribution metadata are usable.
 */
@Singleton
class WiktionaryAudioRepository internal constructor(
    @Named("wikimedia") private val client: OkHttpClient,
    private val wiktionaryApi: HttpUrl,
    private val commonsApi: HttpUrl,
    private val minimumRequestIntervalMillis: Long,
) {
    @Inject
    constructor(
        @Named("wikimedia") client: OkHttpClient,
    ) : this(client, WIKTIONARY_API.toHttpUrl(), COMMONS_API.toHttpUrl(), MIN_REQUEST_INTERVAL_MILLIS)

    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, CacheEntry>(CACHE_LIMIT + 1, 0.75f, true)
    private var lastRequestAtMillis = 0L
    private var blockedUntilMillis = 0L
    private var transientFailure = false

    suspend fun findPronunciation(
        headword: String,
        languageCode: String,
    ): WikimediaPronunciation? =
        withContext(Dispatchers.IO) {
            val word = headword.trim()
            val code = canonicalLanguageCode(languageCode) ?: return@withContext null
            if (word.isEmpty() || word.length > MAX_HEADWORD_LENGTH) return@withContext null

            val key = "$code:$word"
            mutex.withLock {
                cache[key]?.let { return@withLock it.value }
                if (System.currentTimeMillis() < blockedUntilMillis) return@withLock null

                transientFailure = false
                val audio = lookup(word, code)
                if (!transientFailure && System.currentTimeMillis() >= blockedUntilMillis) remember(key, audio)
                audio
            }
        }

    private suspend fun lookup(
        word: String,
        languageCode: String,
    ): WikimediaPronunciation? =
        fetchWiktionaryText(word)
            ?.let { extractAudioFile(it, languageCode, word) }
            ?.let { fileName -> fetchCommonsPronunciation(fileName, word, languageCode) }

    private suspend fun fetchWiktionaryText(word: String): String? {
        val pageUrl =
            wiktionaryApi
                .newBuilder()
                .addQueryParameter("action", "parse")
                .addQueryParameter("format", "json")
                .addQueryParameter("formatversion", "2")
                .addQueryParameter("prop", "wikitext")
                .addQueryParameter("page", word)
                .build()
        val pageBody = requestBody(pageUrl, MAX_WIKTIONARY_RESPONSE_BYTES) ?: return null
        if (pageBody.length > MAX_WIKTIONARY_RESPONSE_CHARS) {
            transientFailure = true
            return null
        }
        return runCatching {
            Json
                .parseToJsonElement(pageBody)
                .jsonObject
                .objectAt("parse")
                ?.stringAt("wikitext")
        }.onFailure { transientFailure = true }.getOrNull()
    }

    private suspend fun fetchCommonsPronunciation(
        fileName: String,
        word: String,
        languageCode: String,
    ): WikimediaPronunciation? {
        val commonsUrl =
            commonsApi
                .newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("format", "json")
                .addQueryParameter("formatversion", "2")
                .addQueryParameter("titles", "File:$fileName")
                .addQueryParameter("prop", "imageinfo")
                .addQueryParameter("iiprop", "url|mime|extmetadata")
                .build()
        val commonsBody = requestBody(commonsUrl, MAX_API_RESPONSE_BYTES) ?: return null
        if (runCatching { Json.parseToJsonElement(commonsBody) }.isFailure) {
            transientFailure = true
            return null
        }
        return parseCommonsResponse(commonsBody, fileName, word, languageCode)
    }

    private suspend fun requestBody(
        url: HttpUrl,
        maxResponseBytes: Long,
    ): String? {
        if (!awaitRequestSlot()) return null

        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Api-User-Agent", USER_AGENT)
                .get()
                .build()
        lastRequestAtMillis = System.currentTimeMillis()
        return runCatching {
            client.newCall(request).execute().use { response -> response.readApiBody(maxResponseBytes) }
        }.onFailure { transientFailure = true }.getOrNull()
    }

    private suspend fun awaitRequestSlot(): Boolean {
        val now = System.currentTimeMillis()
        if (now < blockedUntilMillis) return false
        val waitMillis = minimumRequestIntervalMillis - (now - lastRequestAtMillis)
        if (lastRequestAtMillis > 0 && waitMillis > 0) delay(waitMillis)
        return System.currentTimeMillis() >= blockedUntilMillis
    }

    private fun Response.readApiBody(maxResponseBytes: Long): String? {
        val responseBody = body
        return when {
            code == HTTP_TOO_MANY_REQUESTS || code == HTTP_SERVICE_UNAVAILABLE -> {
                transientFailure = true
                val retryAfterSeconds = parseRetryAfterSeconds(header("Retry-After"))
                blockedUntilMillis =
                    retryAfterDeadlineMillis(
                        nowMillis = System.currentTimeMillis(),
                        retryAfterSeconds = retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS,
                    )
                null
            }
            !isSuccessful -> {
                transientFailure = true
                null
            }
            responseBody == null -> {
                transientFailure = true
                null
            }
            else -> responseBody.readLimited(maxResponseBytes)
        }
    }

    private fun ResponseBody.readLimited(maxResponseBytes: Long): String? {
        if (contentLength() > maxResponseBytes) {
            transientFailure = true
            return null
        }
        val source = source()
        val buffer = Buffer()
        while (buffer.size < maxResponseBytes + 1) {
            val remaining = maxResponseBytes + 1 - buffer.size
            val bytesRead = source.read(buffer, minOf(READ_BUFFER_BYTES, remaining))
            if (bytesRead == -1L) break
        }
        if (buffer.size > maxResponseBytes) {
            transientFailure = true
            return null
        }
        return buffer.readUtf8()
    }

    internal fun parseCommonsResponse(
        body: String,
        fileName: String,
        word: String,
        languageCode: String,
    ): WikimediaPronunciation? =
        runCatching {
            val info = parseFirstImageInfo(body) ?: return null
            val audioUrl = info.stringAt("url")?.secureAudioUrl() ?: return null
            if (info.stringAt("mime")?.isSupportedAudioMime() != true) return null

            val metadata = info.objectAt("extmetadata") ?: return null
            val license = metadata.reusableLicense() ?: return null
            val author = metadata.attribution() ?: return null
            val filePageUrl = info.stringAt("descriptionurl")?.secureCommonsPage() ?: return null
            val sourcePageUrl = buildWiktionaryPageUrl(word, languageCode) ?: return null

            WikimediaPronunciation(
                audioUrl = audioUrl,
                fileName = fileName,
                filePageUrl = filePageUrl,
                sourcePageUrl = sourcePageUrl,
                author = author,
                licenseName = license.first,
                licenseUrl = license.second,
            )
        }.getOrNull()

    private fun parseFirstImageInfo(body: String): JsonObject? =
        runCatching {
            Json
                .parseToJsonElement(body)
                .jsonObject
                .objectAt("query")
                ?.arrayAt("pages")
                ?.firstOrNull()
                ?.jsonObject
                ?.arrayAt("imageinfo")
                ?.firstOrNull()
                ?.jsonObject
        }.getOrNull()

    private fun JsonObject.reusableLicense(): Pair<String, String>? {
        val name = metadataText("LicenseShortName") ?: return null
        if (!isReusableLicense(name)) return null
        val url = metadataRaw("LicenseUrl")?.secureLicenseUrl() ?: return null
        if (!licenseUrlMatchesName(name, url)) return null
        return name to url
    }

    private fun JsonObject.attribution(): String? =
        metadataText("Artist")
            ?: metadataText("Attribution")
            ?: metadataText("Credit")

    private fun remember(
        key: String,
        value: WikimediaPronunciation?,
    ) {
        cache[key] = CacheEntry(value)
        while (cache.size > CACHE_LIMIT) {
            cache.remove(cache.entries.first().key)
        }
    }

    private data class CacheEntry(
        val value: WikimediaPronunciation?,
    )

    companion object {
        private const val WIKTIONARY_API = "https://en.wiktionary.org/w/api.php"
        private const val COMMONS_API = "https://commons.wikimedia.org/w/api.php"
        private const val USER_AGENT = "LinguaAI/1.0 (https://github.com/JasonTM17/Language_Kotlin_App)"
        private const val MAX_HEADWORD_LENGTH = 100
        private const val MAX_API_RESPONSE_BYTES = 1_000_000L
        private const val MAX_WIKTIONARY_RESPONSE_BYTES = 500_000L
        private const val MAX_WIKTIONARY_RESPONSE_CHARS = 500_000
        private const val CACHE_LIMIT = 128
        private const val MIN_REQUEST_INTERVAL_MILLIS = 750L
        private const val DEFAULT_RETRY_AFTER_SECONDS = 5L
        private const val READ_BUFFER_BYTES = 8_192L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVICE_UNAVAILABLE = 503
        private const val MAX_AUDIO_FILE_NAME_CHARS = 240
        private const val AUDIO_TEMPLATE_PARAMETERS_GROUP = 3

        private val TOP_LEVEL_HEADING = Regex("^==([^=].*?)==\\s*$")
        private val LANGUAGE_CODE_TEMPLATE = Regex("(?i)\\{\\{\\s*=\\s*([a-z]{2,3}(?:-[a-z0-9]+)?)\\s*=\\s*\\}\\}")
        private val LANGUAGE_TEMPLATE = Regex("(?i)\\{\\{\\s*([a-z]{2,3})\\s*\\}\\}")
        private val LANGUAGE_PARAMETER = Regex("(?i)\\{\\{\\s*(?:lang|lengua)\\s*\\|\\s*([a-z]{2,3})")
        private val AUDIO_TEMPLATE = Regex("(?i)\\{\\{\\s*audio\\s*\\|\\s*([a-z0-9-]+)\\s*\\|\\s*([^|}\\r\\n]+)([^}\\r\\n]*)")
        private val AUDIO_TEXT_PARAMETER = Regex("(?i)(?:^|\\|)\\s*text\\s*=\\s*([^|}\\r\\n]+)")
        private val NAMED_AUDIO = Regex("(?i)(\\d+audio\\d*|audio\\d*|a|ma)\\s*=\\s*([^|}\\r\\n]+)")
        private val AUDIO_EXTENSION = Regex("(?i)\\.(?:oga|ogg|mp3|wav|flac)$")
        private val HTML_TAG = Regex("<[^>]*>")
        private val HTML_ENTITY = Regex("&(?:amp|lt|gt|quot|apos|nbsp|#39);", RegexOption.IGNORE_CASE)
        private val HREF = Regex("(?i)href=[\"']([^\"']+)[\"']")

        private val CC_LICENSE_NAME = Regex("cc by(-sa)?\\s+(\\d+(?:\\.\\d+)*)(?:\\s+.*)?")

        private val LANGUAGE_NAMES =
            mapOf(
                "ar" to setOf("arabic"),
                "de" to setOf("german"),
                "en" to setOf("english"),
                "es" to setOf("spanish"),
                "fr" to setOf("french"),
                "hi" to setOf("hindi"),
                "id" to setOf("indonesian"),
                "it" to setOf("italian"),
                "ja" to setOf("japanese"),
                "ko" to setOf("korean"),
                "nl" to setOf("dutch"),
                "pt" to setOf("portuguese"),
                "ru" to setOf("russian"),
                "th" to setOf("thai"),
                "tr" to setOf("turkish"),
                "vi" to setOf("vietnamese"),
                "zh" to setOf("chinese"),
            )

        internal fun extractAudioFile(
            wikitext: String,
            languageCode: String,
            headword: String? = null,
        ): String? {
            val code = canonicalLanguageCode(languageCode) ?: return null
            var inTargetSection = false
            val section = StringBuilder()
            for (line in wikitext.lineSequence()) {
                val heading = TOP_LEVEL_HEADING.matchEntire(line.trim())
                if (heading != null) {
                    if (inTargetSection) break
                    inTargetSection = headingMatches(heading.groupValues[1], code)
                } else if (inTargetSection) {
                    section.append(line).append('\n')
                }
            }
            if (!inTargetSection && section.isEmpty()) return null

            val content = section.toString()
            val candidates = linkedSetOf<String>()
            candidates.addAll(audioTemplateFiles(content, code, headword))
            NAMED_AUDIO.findAll(content).forEach { match ->
                val parameter = match.groupValues[1].lowercase(Locale.ROOT)
                if (isNamedAudioParameterForLanguage(parameter, code)) {
                    match.groupValues[2]
                        .trim()
                        .removePrefix("File:")
                        .takeIf(::isAudioFile)
                        ?.let(candidates::add)
                }
            }
            return candidates.firstOrNull()
        }

        private fun audioTemplateFiles(
            content: String,
            languageCode: String,
            headword: String?,
        ): List<String> {
            val files = mutableListOf<String>()
            for (match in AUDIO_TEMPLATE.findAll(content)) {
                val parameters = match.groupValues[AUDIO_TEMPLATE_PARAMETERS_GROUP]
                val audioLabel =
                    AUDIO_TEXT_PARAMETER
                        .find(parameters)
                        ?.groupValues
                        ?.get(1)
                        ?.trim()
                if (
                    canonicalLanguageCode(match.groupValues[1]) == languageCode &&
                    audioLabelMatches(audioLabel, headword)
                ) {
                    val fileName = match.groupValues[2].trim().removePrefix("File:")
                    if (isAudioFile(fileName)) files += fileName
                }
            }
            return files
        }

        private fun audioLabelMatches(
            audioLabel: String?,
            headword: String?,
        ): Boolean {
            if (audioLabel.isNullOrBlank() || headword.isNullOrBlank()) return true
            return normalizeAudioLabel(audioLabel) == normalizeAudioLabel(headword)
        }

        private fun normalizeAudioLabel(value: String): String {
            val words = value.trim().split(Regex("\\s+"))
            return words.joinToString(" ").lowercase(Locale.ROOT)
        }

        internal fun isReusableLicense(licenseName: String): Boolean {
            val normalized = licenseName.trim().lowercase(Locale.ROOT)
            return normalized == "cc0" ||
                normalized.startsWith("public domain") ||
                Regex("^cc by(?:-sa)?\\s+\\d+(?:\\.\\d+)*(?:\\s|$)").containsMatchIn(normalized)
        }

        private fun licenseUrlMatchesName(
            licenseName: String,
            licenseUrl: String,
        ): Boolean {
            val url = licenseUrl.toHttpUrlOrNull() ?: return false
            val normalizedName = licenseName.trim().lowercase(Locale.ROOT)
            val expectedPath =
                when {
                    normalizedName == "cc0" -> "publicdomain/zero/1.0"
                    normalizedName.startsWith("public domain") -> "publicdomain/mark/1.0"
                    else -> {
                        val match = CC_LICENSE_NAME.matchEntire(normalizedName) ?: return false
                        val variant = if (match.groupValues[1].isEmpty()) "by" else "by-sa"
                        "licenses/$variant/${match.groupValues[2]}"
                    }
                }
            val path = url.encodedPath.trim('/').lowercase(Locale.ROOT)
            return path == expectedPath || path.startsWith("$expectedPath/deed.")
        }

        private fun isNamedAudioParameterForLanguage(
            parameter: String,
            languageCode: String,
        ): Boolean =
            when {
                parameter == "a" -> languageCode == "ja"
                parameter == "ma" -> languageCode == "zh"
                parameter.matches(Regex("\\d+audio\\d*")) -> languageCode == "es"
                parameter.startsWith("audio") -> true
                else -> false
            }

        internal fun canonicalLanguageCode(code: String): String? {
            val normalized = code.trim().lowercase(Locale.ROOT).substringBefore('-')
            return normalized.takeIf(LANGUAGE_NAMES::containsKey)
        }

        private fun headingMatches(
            rawHeading: String,
            targetCode: String,
        ): Boolean {
            val code =
                LANGUAGE_CODE_TEMPLATE.find(rawHeading)?.groupValues?.get(1)
                    ?: LANGUAGE_PARAMETER.find(rawHeading)?.groupValues?.get(1)
                    ?: LANGUAGE_TEMPLATE.find(rawHeading)?.groupValues?.get(1)
            if (code != null) return canonicalLanguageCode(code) == targetCode
            val normalized = rawHeading.replace(Regex("\\{\\{[^}]*\\}\\}"), "").trim().lowercase(Locale.ROOT)
            return normalized in LANGUAGE_NAMES[targetCode].orEmpty()
        }

        private fun isAudioFile(value: String): Boolean =
            value.length <= MAX_AUDIO_FILE_NAME_CHARS && AUDIO_EXTENSION.containsMatchIn(value)

        private fun buildWiktionaryPageUrl(
            word: String,
            languageCode: String,
        ): String? {
            val url =
                "https://en.wiktionary.org"
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("wiki")
                    .addPathSegment(word)
                    .build()
            val anchor = LANGUAGE_NAMES[languageCode]?.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: return null
            return url
                .newBuilder()
                .fragment(anchor)
                .build()
                .toString()
        }

        private fun parseRetryAfterSeconds(value: String?): Long? {
            value?.toLongOrNull()?.let { return it }
            if (value.isNullOrBlank()) return null
            val retryTime =
                runCatching {
                    java.text
                        .SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }
                        .parse(value)
                        ?.time
                }.getOrNull() ?: return null
            return ((retryTime - System.currentTimeMillis()) / MILLIS_PER_SECOND).coerceAtLeast(1L)
        }

        private fun String.secureAudioUrl(): String? {
            val url = toHttpUrlOrNull() ?: return null
            if (url.isHttps.not() || url.host != "upload.wikimedia.org") return null
            return url.toString()
        }

        private fun String.isSupportedAudioMime(): Boolean =
            lowercase(Locale.ROOT) in
                setOf("audio/ogg", "application/ogg", "audio/mpeg", "audio/wav", "audio/x-wav", "audio/flac")

        private fun String.secureCommonsPage(): String? {
            val url = toHttpUrlOrNull() ?: return null
            if (!url.isHttps || url.host != "commons.wikimedia.org") return null
            return url.toString()
        }

        private fun String.secureLicenseUrl(): String? {
            val target = HREF.find(this)?.groupValues?.get(1) ?: this.trim()
            val https =
                when {
                    target.startsWith("//") -> "https:$target"
                    target.startsWith("http://", ignoreCase = true) -> target.replaceFirst("http://", "https://", ignoreCase = true)
                    else -> target
                }
            val url = https.toHttpUrlOrNull() ?: return null
            if (!url.isHttps || url.host !in setOf("creativecommons.org", "www.gnu.org")) return null
            return url.toString()
        }

        private fun JsonObject.objectAt(key: String): JsonObject? = runCatching { get(key)?.jsonObject }.getOrNull()

        private fun JsonObject.arrayAt(key: String): JsonArray? = runCatching { get(key)?.jsonArray }.getOrNull()

        private fun JsonObject.stringAt(key: String): String? = runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()

        private fun JsonObject.metadataText(key: String): String? {
            val raw = metadataRaw(key) ?: return null
            return cleanMetadata(raw).takeIf(String::isNotBlank)
        }

        private fun JsonObject.metadataRaw(key: String): String? = objectAt(key)?.stringAt("value")

        private fun cleanMetadata(raw: String): String =
            HTML_ENTITY
                .replace(HTML_TAG.replace(raw, "")) { match ->
                    when (match.value.lowercase(Locale.ROOT)) {
                        "&amp;" -> "&"
                        "&lt;" -> "<"
                        "&gt;" -> ">"
                        "&quot;" -> "\""
                        "&apos;", "&#39;" -> "'"
                        "&nbsp;" -> " "
                        else -> ""
                    }
                }.trim()
                .take(MAX_CREDIT_CHARS)

        private fun String.toHttpUrlOrNull(): HttpUrl? = runCatching { toHttpUrl() }.getOrNull()

        private const val MAX_CREDIT_CHARS = 240
    }
}

internal fun retryAfterDeadlineMillis(
    nowMillis: Long,
    retryAfterSeconds: Long,
): Long {
    val safeSeconds = retryAfterSeconds.coerceAtLeast(1L)
    if (safeSeconds > Long.MAX_VALUE / 1_000L) return Long.MAX_VALUE
    val delayMillis = safeSeconds * 1_000L
    return if (nowMillis > Long.MAX_VALUE - delayMillis) Long.MAX_VALUE else nowMillis + delayMillis
}

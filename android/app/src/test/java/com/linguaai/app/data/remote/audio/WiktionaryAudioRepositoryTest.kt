package com.linguaai.app.data.remote.audio

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class WiktionaryAudioRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: WiktionaryAudioRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            WiktionaryAudioRepository(
                client = OkHttpClient(),
                wiktionaryApi = server.url("/wiktionary").toString().toHttpUrl(),
                commonsApi = server.url("/commons").toString().toHttpUrl(),
                minimumRequestIntervalMillis = 0,
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `selects audio only from the requested Wiktionary language section`() {
        val wikitext =
            """
            ==Chinese==
            {{zh-pron|ma=Zh-huánjìng.ogg}}
            ==Japanese==
            {{ja-pron|かんきょう|acc=0}}
            ==English==
            {{audio|en|En-us-environment.ogg}}
            """.trimIndent()

        assertNull(WiktionaryAudioRepository.extractAudioFile(wikitext, "ja"))
        assertEquals("Zh-huánjìng.ogg", WiktionaryAudioRepository.extractAudioFile(wikitext, "zh"))
        assertEquals("En-us-environment.ogg", WiktionaryAudioRepository.extractAudioFile(wikitext, "en"))
    }

    @Test
    fun `supports native Japanese and Spanish audio templates`() {
        val japanese = "==Japanese==\n{{ja-pron|がくせい|a=Ja-gakusei-anglonative.oga}}"
        val spanish = "==Spanish==\n{{pron-graf|1audio1=Es-hola.oga|h1=ola}}"

        assertEquals("Ja-gakusei-anglonative.oga", WiktionaryAudioRepository.extractAudioFile(japanese, "ja"))
        assertEquals("Es-hola.oga", WiktionaryAudioRepository.extractAudioFile(spanish, "es"))
    }

    @Test
    fun `skips an audio label that pronounces a phrase instead of the headword`() {
        val english =
            """
            ==English==
            * {{audio|en|En-uk-a cat.ogg|a=RP|text=a cat}}
            * {{audio|en|En-us-cat.ogg|a=GA}}
            """.trimIndent()

        assertEquals("En-us-cat.ogg", WiktionaryAudioRepository.extractAudioFile(english, "en", "cat"))
    }

    @Test
    fun `does not accept named audio parameters for a different language`() {
        val japaneseWithChineseAudio = "==Japanese==\n{{ja-pron|かんきょう|ma=Zh-huánjìng.ogg}}"
        val chineseWithJapaneseAudio = "==Chinese==\n{{zh-pron|環境|a=Ja-kankyou.oga}}"

        assertNull(WiktionaryAudioRepository.extractAudioFile(japaneseWithChineseAudio, "ja"))
        assertNull(WiktionaryAudioRepository.extractAudioFile(chineseWithJapaneseAudio, "zh"))
    }

    @Test
    fun `queries Wiktionary and Commons on demand and reuses verified metadata cache`() =
        runBlocking {
            server.enqueue(
                jsonResponse(
                    """{"parse":{"wikitext":"==English==\n{{audio|en|En-us-cat.ogg}}"}}""",
                ),
            )
            server.enqueue(
                jsonResponse(
                    """
                    {"query":{"pages":[{"imageinfo":[{"url":"https://upload.wikimedia.org/wikipedia/commons/a/audio.ogg",
                    "descriptionurl":"https://commons.wikimedia.org/wiki/File:En-us-cat.ogg","mime":"audio/ogg",
                    "extmetadata":{"LicenseShortName":{"value":"CC BY-SA 3.0"},
                    "LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/3.0/"},"Artist":{"value":"Reader"}}}]}]}}
                    """.trimIndent(),
                ),
            )

            val result = repository.findPronunciation("cat", "en")

            assertEquals("https://upload.wikimedia.org/wikipedia/commons/a/audio.ogg", result?.audioUrl)
            assertEquals("Reader", result?.author)
            assertEquals("CC BY-SA 3.0", result?.licenseName)
            val wiktionaryRequest = server.takeRequest(1, TimeUnit.SECONDS)
            val commonsRequest = server.takeRequest(1, TimeUnit.SECONDS)
            assertEquals("/wiktionary", wiktionaryRequest?.requestUrl?.encodedPath)
            assertEquals("cat", wiktionaryRequest?.requestUrl?.queryParameter("page"))
            assertEquals("/commons", commonsRequest?.requestUrl?.encodedPath)
            assertEquals(
                "LinguaAI/1.0 (https://github.com/JasonTM17/Language_Kotlin_App)",
                wiktionaryRequest?.getHeader("User-Agent"),
            )
            assertNull(wiktionaryRequest?.getHeader("Authorization"))
            assertEquals(result, repository.findPronunciation("cat", "en"))
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        }

    @Test
    fun `does not query Commons when the exact language section has no audio`() =
        runBlocking {
            server.enqueue(jsonResponse("""{"parse":{"wikitext":"==Japanese==\n{{ja-pron|かんきょう}}"}}"""))

            assertNull(repository.findPronunciation("環境", "ja"))

            val request = server.takeRequest(1, TimeUnit.SECONDS)
            assertEquals("環境", request?.requestUrl?.queryParameter("page"))
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        }

    @Test
    fun `parses reusable Commons metadata and upgrades trusted legacy license link`() {
        val json =
            """
            {
              "query": {"pages": [{"imageinfo": [{
                "url": "https://upload.wikimedia.org/wikipedia/commons/9/97/Ja-gakusei.oga",
                "descriptionurl": "https://commons.wikimedia.org/wiki/File:Ja-gakusei.oga",
                "mime": "application/ogg",
                "extmetadata": {
                  "LicenseShortName": {"value": "CC0"},
                  "LicenseUrl": {"value": "http://creativecommons.org/publicdomain/zero/1.0/deed.en"},
                  "Artist": {"value": "<a href=\"//commons.wikimedia.org/wiki/User:Reader\">Reader</a>"}
                }
              }]}]}
            }
            """.trimIndent()

        val result = repository.parseCommonsResponse(json, "Ja-gakusei.oga", "学生", "ja")

        assertNotNull(result)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/9/97/Ja-gakusei.oga", result?.audioUrl)
        assertEquals("Reader", result?.author)
        assertEquals("CC0", result?.licenseName)
        assertEquals("https://creativecommons.org/publicdomain/zero/1.0/deed.en", result?.licenseUrl)
        assertTrue(result?.sourcePageUrl.orEmpty().contains("/wiki/%E5%AD%A6%E7%94%9F#Japanese"))
    }

    @Test
    fun `rejects non reusable licenses and non Wikimedia audio hosts`() {
        val unlicensed = commonsJson(audioUrl = "https://upload.wikimedia.org/audio.ogg", license = "All rights reserved")
        val untrustedHost = commonsJson(audioUrl = "https://example.test/audio.ogg", license = "CC BY-SA 4.0")
        val insecure = commonsJson(audioUrl = "http://upload.wikimedia.org/audio.ogg", license = "CC BY-SA 4.0")

        assertNull(repository.parseCommonsResponse(unlicensed, "audio.ogg", "word", "en"))
        assertNull(repository.parseCommonsResponse(untrustedHost, "audio.ogg", "word", "en"))
        assertNull(repository.parseCommonsResponse(insecure, "audio.ogg", "word", "en"))
    }

    @Test
    fun `rejects a license link whose version does not match its displayed name`() {
        val mismatchedVersion = commonsJson(license = "CC BY-SA 3.0", licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/")
        val mismatchedVariant = commonsJson(license = "CC BY 4.0", licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/")
        val matchingBy = commonsJson(license = "CC BY 4.0", licenseUrl = "https://creativecommons.org/licenses/by/4.0/")
        val correctPublicDomain = commonsJson(license = "Public domain", licenseUrl = "https://creativecommons.org/publicdomain/mark/1.0/")

        assertNull(repository.parseCommonsResponse(mismatchedVersion, "audio.ogg", "word", "en"))
        assertNull(repository.parseCommonsResponse(mismatchedVariant, "audio.ogg", "word", "en"))
        assertNotNull(repository.parseCommonsResponse(matchingBy, "audio.ogg", "word", "en"))
        assertNotNull(repository.parseCommonsResponse(correctPublicDomain, "audio.ogg", "word", "en"))
    }

    @Test
    fun `Retry-After deadline honors long values and saturates overflow`() {
        assertEquals(610_000L, retryAfterDeadlineMillis(nowMillis = 10_000L, retryAfterSeconds = 600L))
        assertEquals(Long.MAX_VALUE, retryAfterDeadlineMillis(nowMillis = 10_000L, retryAfterSeconds = Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, retryAfterDeadlineMillis(nowMillis = Long.MAX_VALUE - 500L, retryAfterSeconds = 1L))
    }

    @Test
    fun `long Retry-After blocks another API request`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "600"))

            assertNull(repository.findPronunciation("cat", "en"))
            assertNull(repository.findPronunciation("dog", "en"))

            assertEquals("cat", server.takeRequest(1, TimeUnit.SECONDS)?.requestUrl?.queryParameter("page"))
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        }

    @Test
    fun `rejects unsupported media types and missing attribution links`() {
        val video = commonsJson(mime = "video/ogg", license = "CC BY-SA 4.0")
        val missingAttribution = commonsJson(mime = "audio/ogg", license = "CC BY-SA 4.0", includeArtist = false)

        assertNull(repository.parseCommonsResponse(video, "audio.ogg", "word", "en"))
        assertNull(repository.parseCommonsResponse(missingAttribution, "audio.ogg", "word", "en"))
    }

    @Test
    fun `recognizes the app language catalog codes and rejects unknown codes`() {
        val codes = listOf("ar", "de", "en", "es", "fr", "hi", "id", "it", "ja", "ko", "nl", "pt", "ru", "th", "tr", "vi", "zh")

        codes.forEach { assertEquals(it, WiktionaryAudioRepository.canonicalLanguageCode(it)) }
        assertEquals("en", WiktionaryAudioRepository.canonicalLanguageCode("en-US"))
        assertNull(WiktionaryAudioRepository.canonicalLanguageCode("xx"))
        assertFalse(WiktionaryAudioRepository.isReusableLicense("All rights reserved"))
    }

    private fun commonsJson(
        audioUrl: String = "https://upload.wikimedia.org/audio.ogg",
        mime: String = "audio/ogg",
        license: String,
        licenseUrl: String = "https://creativecommons.org/licenses/by-sa/4.0/",
        includeArtist: Boolean = true,
    ): String {
        val artist = if (includeArtist) "\"Artist\": {\"value\": \"Contributor\"}," else ""
        val credit =
            if (includeArtist) {
                "\"Credit\": {\"value\": \"Contributor\"}"
            } else {
                "\"ImageDescription\": {\"value\": \"Audio pronunciation\"}"
            }
        return """
            {"query":{"pages":[{"imageinfo":[{
              "url":"$audioUrl",
              "descriptionurl":"https://commons.wikimedia.org/wiki/File:audio.ogg",
              "mime":"$mime",
              "extmetadata":{
                "LicenseShortName":{"value":"$license"},
                "LicenseUrl":{"value":"$licenseUrl"},
                $artist
                $credit
              }
            }]}]}}
            """.trimIndent()
    }

    private fun jsonResponse(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}

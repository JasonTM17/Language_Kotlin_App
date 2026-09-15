package com.linguaai.app.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `sources` contract must stay backward compatible: responses from servers
 * without RAG (and cached payloads) omit the field, and the client treats that
 * as "no citations", never a decode failure.
 */
class AiDtosParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `chat response without sources decodes to an empty list`() {
        val payload = """{"conversationId":7,"reply":"こんにちは","mode":"general"}"""
        val response = json.decodeFromString(AiChatResponseDto.serializer(), payload)
        assertEquals(7, response.conversationId)
        assertEquals("こんにちは", response.reply)
        assertTrue(response.sources.isEmpty())
    }

    @Test
    fun `chat response with sources decodes citations`() {
        val payload =
            """
            {
              "conversationId": 7,
              "reply": "環境 means environment.",
              "mode": "general",
              "sources": [
                {
                  "title": "環境 (かんきょう) — Môi trường",
                  "sourceType": "VOCABULARY",
                  "sourceId": 1,
                  "chunkIndex": 0,
                  "level": "N3",
                  "score": 0.42
                }
              ]
            }
            """.trimIndent()
        val response = json.decodeFromString(AiChatResponseDto.serializer(), payload)
        assertEquals(1, response.sources.size)
        val source = response.sources.single()
        assertEquals("環境 (かんきょう) — Môi trường", source.title)
        assertEquals("VOCABULARY", source.sourceType)
        assertEquals(1, source.sourceId)
        assertEquals(0, source.chunkIndex)
        assertEquals("N3", source.level)
        assertEquals(0.42, source.score, 1e-9)
    }

    @Test
    fun `sources without optional level still decode`() {
        val payload =
            """
            {
              "conversationId": 1,
              "reply": "ok",
              "mode": "general",
              "sources": [
                {"title": "t", "sourceType": "GRAMMAR", "sourceId": 2, "chunkIndex": 3, "score": 0.9}
              ]
            }
            """.trimIndent()
        val response = json.decodeFromString(AiChatResponseDto.serializer(), payload)
        assertEquals(null, response.sources.single().level)
    }
}

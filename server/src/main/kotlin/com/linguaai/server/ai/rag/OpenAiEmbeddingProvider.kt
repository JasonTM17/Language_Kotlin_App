package com.linguaai.server.ai.rag

import com.linguaai.server.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible `/embeddings` client. Active only when an OpenAI-compatible
 * provider is configured; it has no offline test coverage because there is no
 * deterministic fixture for a remote embeddings API — a live-key check is part
 * of deployment, not of this suite (recorded in ADR-007).
 */
class OpenAiEmbeddingProvider(
    private val config: AppConfig,
    private val client: HttpClient,
) : EmbeddingProvider {
    override val model: String = config.aiEmbeddingModel

    override val dimensions: Int = KNOWN_DIMENSIONS[model] ?: DEFAULT_DIMENSIONS

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val response =
            client.post("${config.aiBaseUrl.trimEnd('/')}/embeddings") {
                headers.append(HttpHeaders.Authorization, "Bearer ${config.aiApiKey}")
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(EmbeddingsRequest(model = model, input = texts))
            }
        if (!response.status.isSuccess()) {
            error("Embedding provider returned HTTP ${response.status.value}")
        }
        val payload = response.body<EmbeddingsResponse>()
        return payload.data
            .sortedBy { it.index }
            .map { item -> item.embedding.map { it.toFloat() }.toFloatArray() }
    }

    @Serializable
    private data class EmbeddingsRequest(
        val model: String,
        val input: List<String>,
    )

    @Serializable
    private data class EmbeddingsResponse(
        val data: List<EmbeddingItem> = emptyList(),
    )

    @Serializable
    private data class EmbeddingItem(
        val index: Int = 0,
        val embedding: List<Double> = emptyList(),
    )

    private companion object {
        const val DEFAULT_DIMENSIONS = 1536
        val KNOWN_DIMENSIONS = mapOf("text-embedding-3-small" to 1536, "text-embedding-3-large" to 3072)
    }
}

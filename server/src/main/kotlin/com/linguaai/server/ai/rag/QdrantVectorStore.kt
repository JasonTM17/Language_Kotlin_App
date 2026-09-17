package com.linguaai.server.ai.rag

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Qdrant REST vector store — the production engine when QDRANT_URL is set.
 *
 * Point IDs are deterministic UUIDs derived from (sourceType, sourceId,
 * chunkIndex), so re-upserting a chunk overwrites in place and replaceSource
 * stays idempotent. Payloads carry the chunk fields search results need to
 * reconstruct [RetrievedChunk] without touching SQL. Qdrant's cosine `score`
 * is the same cosine similarity the SQL store computes, so scores are
 * comparable in ops stats.
 */
class QdrantVectorStore(
    private val baseUrl: String,
    private val collection: String,
    private val dimensions: Int,
    private val client: HttpClient,
) : VectorStore {
    override val kind: String = "qdrant"

    private var collectionEnsured = false

    override suspend fun writeSources(writes: List<SourceWrite>) {
        if (writes.isEmpty()) return
        ensureCollection()
        for ((sourceType, group) in writes.groupBy { it.sourceType }) {
            // One filtered delete for the whole group, then batched upserts:
            // per-source HTTP round trips capped indexing at ~30 docs/second.
            deleteSources(sourceType, group.map { it.sourceId })
            val points = group.map { write -> toPoints(sourceType, write) }.flatten()
            points.chunked(UPSERT_BATCH).forEach { batch ->
                val response =
                    client.put("$baseUrl/collections/$collection/points?wait=true") {
                        contentType(ContentType.Application.Json)
                        setBody(UpsertRequest(points = batch))
                    }
                if (!response.status.isSuccess()) {
                    error("Qdrant upsert failed with HTTP ${response.status.value}: ${response.bodyAsText().take(ERROR_BODY_SNIPPET)}")
                }
            }
        }
    }

    private fun toPoints(
        sourceType: String,
        write: SourceWrite,
    ): List<UpsertPoint> =
        write.entries.map { entry ->
            UpsertPoint(
                id = pointId(sourceType, write.sourceId, entry.chunkIndex),
                vector = entry.embedding.toList(),
                payload =
                    PointPayload(
                        title = entry.payload.title,
                        content = entry.payload.content,
                        sourceType = sourceType,
                        sourceId = write.sourceId,
                        chunkIndex = entry.chunkIndex,
                        level = entry.payload.level,
                        languageId = write.languageId,
                        embeddingModel = entry.embeddingModel,
                        tokens = LexicalTokenizer.verifierTokens(entry.payload.title + " " + entry.payload.content),
                    ),
            )
        }

    override suspend fun search(query: SearchQuery): List<RetrievedChunk> {
        ensureCollection()
        val response =
            client
                .post("$baseUrl/collections/$collection/points/search") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        SearchRequest(
                            vector = query.vector.toList(),
                            limit = query.topK,
                            filter =
                                FilterRequest(
                                    must = mustFilters(query.embeddingModel, query.languageId, query.level),
                                    should =
                                        query.verifierTokens.map { token ->
                                            matchFilter("tokens", token)
                                        },
                                ),
                            withPayload = true,
                        ),
                    )
                }.body<SearchResponse>()
        return response.result.map { hit ->
            val payload = hit.payload
            RetrievedChunk(
                ref =
                    ChunkRef(
                        title = payload.title,
                        sourceType = payload.sourceType,
                        sourceId = payload.sourceId,
                        chunkIndex = payload.chunkIndex,
                        level = payload.level,
                    ),
                content = payload.content,
                score = hit.score,
            )
        }
    }

    override suspend fun deleteSources(
        sourceType: String,
        sourceIds: Collection<Long>,
    ) {
        if (sourceIds.isEmpty()) return
        ensureCollection()
        // must = type, should = any of the ids (OR). Only the single-value
        // `match` form is used: it is the variant every Qdrant version accepts.
        sourceIds.chunked(DELETE_CHUNK).forEach { chunk ->
            val response =
                client.post("$baseUrl/collections/$collection/points/delete?wait=true") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        DeleteRequest(
                            filter =
                                FilterRequest(
                                    must = listOf(matchFilter("source_type", sourceType)),
                                    should =
                                        chunk.map { id ->
                                            MatchFilter(key = "source_id", match = MatchValue(value = JsonPrimitive(id)))
                                        },
                                ),
                        ),
                    )
                }
            if (!response.status.isSuccess()) {
                error(
                    "Qdrant source delete failed with HTTP ${response.status.value}: ${response.bodyAsText().take(ERROR_BODY_SNIPPET)}",
                )
            }
        }
    }

    override suspend fun countChunks(): Long {
        // The collection info's points_count lags behind the optimizer; the
        // exact count endpoint is the authoritative number for ops stats.
        val response =
            client
                .post("$baseUrl/collections/$collection/points/count") {
                    contentType(ContentType.Application.Json)
                    setBody(ExactCountRequest(exact = true))
                }.body<ExactCountResponse>()
        return response.result.count
    }

    private suspend fun ensureCollection() {
        if (collectionEnsured) return
        val response =
            client.put("$baseUrl/collections/$collection") {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateCollectionRequest(
                        vectors = VectorConfig(size = dimensions, distance = "Cosine"),
                    ),
                )
            }
        // 409 = the collection already exists from a previous deployment: fine.
        if (!response.status.isSuccess() && response.status.value != CONFLICT) {
            error("Qdrant collection creation failed with HTTP ${response.status.value}")
        }
        PAYLOAD_INDEXES.forEach { (fieldName, fieldSchema) ->
            val indexResponse =
                client.put("$baseUrl/collections/$collection/index?wait=true") {
                    contentType(ContentType.Application.Json)
                    setBody(PayloadIndexRequest(fieldName = fieldName, fieldSchema = fieldSchema))
                }
            if (!indexResponse.status.isSuccess() && indexResponse.status.value != CONFLICT) {
                error(
                    "Qdrant payload index creation failed for $fieldName with HTTP ${indexResponse.status.value}",
                )
            }
        }
        collectionEnsured = true
    }

    private fun mustFilters(
        embeddingModel: String,
        languageId: Long,
        level: String?,
    ): List<MatchFilter> =
        buildList {
            add(matchFilter("embedding_model", embeddingModel))
            add(matchFilter("language_id", languageId))
            level?.let { add(matchFilter("level", it)) }
        }

    private fun matchFilter(
        key: String,
        value: String,
    ): MatchFilter = MatchFilter(key = key, match = MatchValue(value = JsonPrimitive(value)))

    private fun matchFilter(
        key: String,
        value: Long,
    ): MatchFilter = MatchFilter(key = key, match = MatchValue(value = JsonPrimitive(value)))

    @Serializable
    private data class VectorConfig(
        val size: Int,
        val distance: String,
    )

    @Serializable
    private data class CreateCollectionRequest(
        val vectors: VectorConfig,
    )

    @Serializable
    private data class PayloadIndexRequest(
        @SerialName("field_name") val fieldName: String,
        @SerialName("field_schema") val fieldSchema: String,
    )

    @Serializable
    private data class MatchValue(
        val value: JsonElement,
    )

    @Serializable
    private data class MatchFilter(
        val key: String,
        val match: MatchValue,
    )

    @Serializable
    private data class FilterRequest(
        val must: List<MatchFilter> = emptyList(),
        val should: List<MatchFilter> = emptyList(),
    )

    @Serializable
    private data class PointPayload(
        /** Wire keys are snake_case: search filters address payload fields by name. */
        @SerialName("title") val title: String,
        @SerialName("content") val content: String,
        @SerialName("source_type") val sourceType: String,
        @SerialName("source_id") val sourceId: Long,
        @SerialName("chunk_index") val chunkIndex: Int,
        @SerialName("level") val level: String?,
        @SerialName("language_id") val languageId: Long,
        @SerialName("embedding_model") val embeddingModel: String,
        /**
         * Exact verifier tokens of this chunk. Recall filters on them so a
         * 1M-point collection cannot bury the true match under hash-bin
         * collisions — keyword match is exact, cosine ranking then orders
         * only the token-sharing survivors.
         */
        @SerialName("tokens") val tokens: List<String> = emptyList(),
    )

    @Serializable
    private data class UpsertPoint(
        val id: String,
        val vector: List<Float>,
        val payload: PointPayload,
    )

    @Serializable
    private data class UpsertRequest(
        val points: List<UpsertPoint>,
    )

    @Serializable
    private data class DeleteRequest(
        val filter: FilterRequest,
    )

    @Serializable
    private data class SearchRequest(
        val vector: List<Float>,
        val limit: Int,
        val filter: FilterRequest,
        /**
         * Qdrant omits payloads unless asked and hits are reconstructed from
         * them. No default: kotlinx omits fields equal to their default value
         * (encodeDefaults=false), which would silently drop the flag.
         */
        @SerialName("with_payload") val withPayload: Boolean,
    )

    @Serializable
    private data class SearchHit(
        val id: String,
        val score: Double,
        val payload: PointPayload,
    )

    @Serializable
    private data class SearchResponse(
        val result: List<SearchHit> = emptyList(),
    )

    @Serializable
    private data class ExactCountRequest(
        val exact: Boolean,
    )

    @Serializable
    private data class ExactCount(
        val count: Long = 0,
    )

    @Serializable
    private data class ExactCountResponse(
        val result: ExactCount = ExactCount(),
    )

    companion object {
        const val UPSERT_BATCH = 1000
        const val DELETE_CHUNK = 500
        const val ERROR_BODY_SNIPPET = 400

        /** Qdrant answers 409 when the collection already exists. */
        const val CONFLICT = 409

        /** Indexed payload filters keep purge and language-scoped search bounded. */
        val PAYLOAD_INDEXES =
            listOf(
                "source_id" to "integer",
                "source_type" to "keyword",
                "language_id" to "integer",
                "embedding_model" to "keyword",
                "level" to "keyword",
                // Exact-token recall: keyword index over the chunk's verifier
                // tokens so filtered search cannot be buried in hash-bin
                // collisions on million-point collections.
                "tokens" to "keyword",
            )

        /** Stable across runs: re-upsert overwrites instead of duplicating. */
        fun pointId(
            sourceType: String,
            sourceId: Long,
            chunkIndex: Int,
        ): String = UUID.nameUUIDFromBytes("$sourceType:$sourceId:$chunkIndex".encodeToByteArray()).toString()
    }
}

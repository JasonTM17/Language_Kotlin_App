package com.linguaai.server.ai.rag

/** Identity of one corpus chunk, shared by stored entries and retrieval hits. */
class ChunkRef(
    val title: String,
    val sourceType: String,
    val sourceId: Long,
    val chunkIndex: Int,
    val level: String?,
)

/** The teachable text of a chunk, kept separate from its vector bookkeeping. */
class ChunkPayload(
    val title: String,
    val content: String,
    val level: String?,
)

/**
 * One stored corpus chunk with its vector. Kept as a plain class (not a data
 * class) because float-array equality is identity-based and would lie.
 */
class ChunkEntry(
    val chunkIndex: Int,
    val payload: ChunkPayload,
    /** Identity of the embedding space this vector belongs to. */
    val embeddingModel: String,
    /** SHA-256 of the payload content; the idempotency key for re-indexing. */
    val contentHash: String,
    val embedding: FloatArray,
)

/** A retrieval hit: chunk identity plus its similarity score. */
class RetrievedChunk(
    val ref: ChunkRef,
    val content: String,
    val score: Double,
)

/** All chunks of one corpus source, ready to be written wholesale. */
class SourceWrite(
    val sourceType: String,
    val sourceId: Long,
    val languageId: Long,
    val entries: List<ChunkEntry>,
)

/** Search input bundled to keep engine signatures narrow. */
class SearchQuery(
    val vector: FloatArray,
    val embeddingModel: String,
    val languageId: Long,
    val level: String?,
    val topK: Int,
    /** Exact query tokens for engines that support in-store token recall. */
    val verifierTokens: List<String> = emptyList(),
)

/**
 * The seam between the RAG pipeline and a vector engine.
 *
 * Selection is a config decision made once at wiring time (QDRANT_URL present
 * -> Qdrant, otherwise SQL brute-force over the knowledge_chunks BLOBs).
 * There is deliberately no runtime try/catch fallback: silently switching
 * engines mid-flight would trade a hard failure for corrupted retrieval.
 */
interface VectorStore {
    /** Identity used in ops stats and E2E evidence ("sql" or "qdrant"). */
    val kind: String

    /**
     * Idempotent batch write: previous chunks of every listed source are
     * replaced. Batched because per-source HTTP round trips (Qdrant) or per-
     * source transactions (SQL) are what bound indexing throughput at
     * 100k+ sources.
     */
    suspend fun writeSources(writes: List<SourceWrite>)

    /**
     * Cosine search restricted to one language and one embedding model —
     * vectors from different models are different spaces and must never mix.
     */
    suspend fun search(query: SearchQuery): List<RetrievedChunk>

    /**
     * Removes every chunk of the given sources from THIS store. Derived
     * engines must be cleaned alongside the canonical store or purged corpus
     * content stays retrievable (the seed teardown path).
     */
    suspend fun deleteSources(
        sourceType: String,
        sourceIds: Collection<Long>,
    )

    suspend fun countChunks(): Long
}

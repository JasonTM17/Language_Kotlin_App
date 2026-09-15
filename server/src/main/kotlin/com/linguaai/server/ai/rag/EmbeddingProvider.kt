package com.linguaai.server.ai.rag

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Produces embedding vectors for corpus chunks and user queries.
 *
 * The active model id travels with every stored vector (KnowledgeChunks.embeddingModel)
 * so vectors from different providers are never compared against each other:
 * a config flip between the hashing provider and a real embedding API degrades
 * retrieval to no-context instead of returning garbage cosine scores.
 */
interface EmbeddingProvider {
    /** Identity of the embedding space; stored per chunk and asserted at query time. */
    val model: String

    val dimensions: Int

    suspend fun embed(texts: List<String>): List<FloatArray>
}

/**
 * Deterministic feature-hashing embedder — the offline default.
 *
 * Honest labeling (ADR-007): this is lexical retrieval, not semantic. What it
 * buys is zero-dependency, reproducible retrieval for development, tests and
 * the mock provider path; semantic embeddings activate when an OpenAI-compatible
 * provider is configured.
 *
 * Feature design (each choice earns its place by a measured failure):
 *  - Word tokens carry [WORD_WEIGHT]x mass: exact vocabulary dominates ranking.
 *  - Char bigrams/trigrams are generated ONLY inside CJK runs. Generating them
 *    over Latin text buried word signals under hundreds of near-noise features
 *    (an exact match scored 0.17 while a collision scored 0.20); CJK runs keep
 *    環境-style short queries matchable without the dilution.
 *  - 1024 bins: at 256, unrelated features shared a bin often enough to outrank
 *    true matches. 1024 keeps single-collision mass under the retrieval floor.
 *
 * `String.hashCode` is specified by the Java spec, so bins are stable across
 * JVMs and runs — required for idempotent reindexing.
 */
class HashingEmbeddingProvider : EmbeddingProvider {
    override val model: String = "hash-tfidf-$DIMENSIONS"

    override val dimensions: Int = DIMENSIONS

    override suspend fun embed(texts: List<String>): List<FloatArray> = texts.map { embedOne(it) }

    private fun embedOne(raw: String): FloatArray {
        val counts = HashMap<String, FeatureCount>()
        val lowered = raw.lowercase()
        for (token in WORD_TOKENS.findAll(lowered)) {
            bump(counts, "w:${token.value}", WORD_WEIGHT)
        }
        for (run in CJK_RUNS.findAll(lowered)) {
            val text = run.value
            for (size in intArrayOf(BIGRAM_SIZE, TRIGRAM_SIZE)) {
                if (text.length < size) continue
                for (i in 0..text.length - size) {
                    bump(counts, "g:${text.substring(i, i + size)}", 1.0)
                }
            }
        }
        val vector = FloatArray(DIMENSIONS)
        if (counts.isEmpty()) return vector
        for ((feature, entry) in counts) {
            val bin = feature.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % DIMENSIONS
            val mass = ((1.0 + ln(entry.count.toDouble())) * entry.weight).toFloat()
            vector[bin] += mass
        }
        var norm = 0.0
        for (value in vector) norm += value * value
        norm = sqrt(norm)
        if (norm == 0.0) return vector
        for (i in vector.indices) vector[i] = (vector[i] / norm).toFloat()
        return vector
    }

    private fun bump(
        counts: MutableMap<String, FeatureCount>,
        feature: String,
        weight: Double,
    ) {
        val existing = counts[feature]
        if (existing == null) {
            counts[feature] = FeatureCount(count = 1, weight = weight)
        } else {
            existing.count += 1
        }
    }

    private class FeatureCount(
        var count: Int,
        val weight: Double,
    )

    private companion object {
        const val DIMENSIONS = 1024
        const val TRIGRAM_SIZE = 3
        const val BIGRAM_SIZE = 2

        /** Word tokens outweigh char n-grams: exact vocabulary drives ranking. */
        const val WORD_WEIGHT = 3.0

        /** Kana, CJK ideographs and fullwidth forms — the scripts char n-grams serve. */
        val CJK_RUNS = Regex("""[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uff66-\uff9f]+""")
        val WORD_TOKENS = Regex("""[\p{L}\p{N}]+""")
    }
}

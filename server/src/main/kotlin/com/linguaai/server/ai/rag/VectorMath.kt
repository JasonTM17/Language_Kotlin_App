package com.linguaai.server.ai.rag

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Vector and hashing primitives for the RAG pipeline.
 *
 * Embeddings are stored as little-endian float32 BLOBs: 256 dims are 1 KiB
 * instead of the ~2.5 KiB a JSON array would cost, and both MySQL and H2
 * round-trip BLOBs byte-exactly (asserted by a Phase 01 integration test).
 */
object VectorMath {
    private const val FLOAT_BYTES = 4

    fun cosine(
        a: FloatArray,
        b: FloatArray,
    ): Double {
        if (a.size != b.size) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / kotlin.math.sqrt(normA * normB)
    }

    fun isZero(vector: FloatArray): Boolean = vector.all { it == 0f }

    fun toBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * FLOAT_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(vector)
        return buffer.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / FLOAT_BYTES)
        buffer.asFloatBuffer().get(floats)
        return floats
    }

    /** Stable content identity for idempotent indexing (hex-encoded SHA-256). */
    fun sha256Hex(text: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Helper for tests and bulk writers that need a sized buffer. */
    fun emptyBuffer(dimension: Int): ByteArrayOutputStream = ByteArrayOutputStream(dimension * FLOAT_BYTES)
}

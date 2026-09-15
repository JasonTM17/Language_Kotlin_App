package com.linguaai.server.ai.rag

import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The hashing embedder is the offline retrieval default, so its contract is
 * pinned here: deterministic, unit-normalised, and sensitive to shared tokens
 * (a query sharing vocabulary with a document must outscore an unrelated one).
 */
class HashingEmbeddingProviderTest {
    private val embedder = HashingEmbeddingProvider()

    @Test
    fun `embeddings are deterministic across runs`() =
        runBlocking {
            val first = embedder.embed(listOf("thank you very much")).single()
            val second = embedder.embed(listOf("thank you very much")).single()
            assertTrue(first.contentEquals(second), "same text must produce the same vector")
        }

    @Test
    fun `vectors are unit-normalised`() =
        runBlocking {
            val vector = embedder.embed(listOf("how do I order food in a restaurant")).single()
            val norm = sqrt(vector.fold(0.0) { acc, v -> acc + v * v })
            assertEquals(1.0, norm, 1e-6)
        }

    @Test
    fun `related text outscores unrelated text`() =
        runBlocking {
            val doc = embedder.embed(listOf("環境 means environment in Japanese")).single()
            val related = embedder.embed(listOf("what does 環境 mean?")).single()
            val unrelated = embedder.embed(listOf("book a flight to the airport")).single()
            val relatedScore = VectorMath.cosine(related, doc)
            val unrelatedScore = VectorMath.cosine(unrelated, doc)
            assertTrue(relatedScore > unrelatedScore, "related=$relatedScore must beat unrelated=$unrelatedScore")
        }

    @Test
    fun `cjk text produces a non-zero vector`() =
        runBlocking {
            val vector = embedder.embed(listOf("環境を守る")).single()
            assertFalse(VectorMath.isZero(vector))
        }

    @Test
    fun `blank text produces a zero vector and cosine stays zero`() =
        runBlocking {
            val vector = embedder.embed(listOf("   ")).single()
            assertTrue(VectorMath.isZero(vector))
            val other = embedder.embed(listOf("anything")).single()
            assertEquals(0.0, VectorMath.cosine(vector, other))
        }

    @Test
    fun `model identity names the space`() {
        assertEquals("hash-tfidf-1024", embedder.model)
        assertEquals(1024, embedder.dimensions)
    }
}

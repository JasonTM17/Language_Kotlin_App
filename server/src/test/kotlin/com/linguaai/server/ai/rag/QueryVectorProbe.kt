package com.linguaai.server.ai.rag

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test

/** Manual evidence tool: prints the exact embedding the pipeline computes. */
class QueryVectorProbe {
    @Test
    fun `print query vectors`() =
        runBlocking {
            val embedder = HashingEmbeddingProvider()
            val vectorSerializer = ListSerializer(Float.serializer())
            for (query in listOf("kankyou", "環境")) {
                val vector = embedder.embed(listOf(query)).first()
                println("QUERY_VECTOR query=$query dims=${vector.size} payload=${Json.encodeToString(vectorSerializer, vector.toList())}")
            }
        }
}

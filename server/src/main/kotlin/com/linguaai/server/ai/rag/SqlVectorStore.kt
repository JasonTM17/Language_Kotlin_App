package com.linguaai.server.ai.rag

import com.linguaai.server.db.KnowledgeChunks
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

/**
 * Brute-force cosine search over the canonical `knowledge_chunks` table.
 *
 * Candidates are filtered in SQL by language and embedding model, then scored
 * in Kotlin. Correct and dependency-free; at large scale the Qdrant engine is
 * the faster path, and measured SQL latency is reported rather than hidden.
 */
class SqlVectorStore(
    private val candidateLimit: Int = DEFAULT_CANDIDATE_LIMIT,
) : VectorStore {
    override val kind: String = "sql"

    /**
     * Single-writer gate. Parallel indexers delete+insert rows sharing the
     * (source_type, source_id, chunk_index) unique index, and MySQL gap locks
     * deadlock on exactly that pattern. Writes are serialized; search stays
     * lock-free, and this is not the indexing bottleneck (embedding is).
     */
    private val writeMutex = Mutex()

    override suspend fun writeSources(writes: List<SourceWrite>) {
        if (writes.isEmpty()) return
        writeMutex.withLock {
            val now = LocalDateTime.now()
            transaction {
                for (write in writes) {
                    KnowledgeChunks.deleteWhere {
                        (KnowledgeChunks.sourceType eq write.sourceType) and
                            (KnowledgeChunks.sourceId eq write.sourceId)
                    }
                    for (entry in write.entries) {
                        KnowledgeChunks.insert {
                            it[KnowledgeChunks.languageId] = write.languageId
                            it[KnowledgeChunks.level] = entry.payload.level
                            it[KnowledgeChunks.sourceType] = write.sourceType
                            it[KnowledgeChunks.sourceId] = write.sourceId
                            it[KnowledgeChunks.chunkIndex] = entry.chunkIndex
                            it[KnowledgeChunks.title] = entry.payload.title
                            it[KnowledgeChunks.content] = entry.payload.content
                            it[KnowledgeChunks.embeddingModel] = entry.embeddingModel
                            it[KnowledgeChunks.embedding] = ExposedBlob(VectorMath.toBytes(entry.embedding))
                            it[KnowledgeChunks.contentHash] = entry.contentHash
                            it[KnowledgeChunks.createdAt] = now
                            it[KnowledgeChunks.updatedAt] = now
                        }
                    }
                }
            }
        }
    }

    override suspend fun search(query: SearchQuery): List<RetrievedChunk> {
        val rows =
            transaction {
                val select =
                    KnowledgeChunks
                        .selectAll()
                        // Deterministic candidate window: without an ordering,
                        // LIMIT returns an engine-dependent row subset, which
                        // quietly degrades retrieval between runs.
                        .orderBy(KnowledgeChunks.id, SortOrder.ASC)
                        .andWhere { KnowledgeChunks.languageId eq query.languageId }
                        .andWhere { KnowledgeChunks.embeddingModel eq query.embeddingModel }
                query.level?.let { select.andWhere { KnowledgeChunks.level eq it } }
                select.limit(candidateLimit).map { it }
            }
        val scored = ArrayList<Pair<RetrievedChunk, Double>>(rows.size)
        for (row in rows) {
            val embedding =
                runCatching {
                    VectorMath.fromBytes(row[KnowledgeChunks.embedding].bytes)
                }.getOrNull() ?: continue
            val score = VectorMath.cosine(query.vector, embedding)
            scored +=
                RetrievedChunk(
                    ref =
                        ChunkRef(
                            title = row[KnowledgeChunks.title],
                            sourceType = row[KnowledgeChunks.sourceType],
                            sourceId = row[KnowledgeChunks.sourceId],
                            chunkIndex = row[KnowledgeChunks.chunkIndex],
                            level = row[KnowledgeChunks.level],
                        ),
                    content = row[KnowledgeChunks.content],
                    score = score,
                ) to score
        }
        return scored
            .sortedByDescending { it.second }
            .take(query.topK)
            .filter { it.second > MIN_SCORE }
            .map { it.first }
    }

    override suspend fun deleteSources(
        sourceType: String,
        sourceIds: Collection<Long>,
    ) {
        if (sourceIds.isEmpty()) return
        writeMutex.withLock {
            sourceIds.chunked(DELETE_CHUNK).forEach { chunk ->
                transaction {
                    KnowledgeChunks.deleteWhere {
                        (KnowledgeChunks.sourceType eq sourceType) and (KnowledgeChunks.sourceId inList chunk)
                    }
                }
            }
        }
    }

    override suspend fun countChunks(): Long = transaction { KnowledgeChunks.selectAll().count() }

    private companion object {
        const val DEFAULT_CANDIDATE_LIMIT = 20_000
        const val DELETE_CHUNK = 500

        /**
         * Absolute noise floor only — ranking precision is the lexical
         * verification stage's job (RagService.verifyLexically), not this
         * threshold's. Kept tiny because long queries dilute cosine mass
         * even for true matches.
         */
        const val MIN_SCORE = 0.01
    }
}

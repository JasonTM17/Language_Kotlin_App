package com.linguaai.server.ai.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class IndexReport(
    val documentsScanned: Long,
    val chunksWritten: Long,
    val documentsUnchanged: Long,
    val durationMs: Long,
)

/**
 * Walks the whole corpus (keyset pagination) and keeps the vector store in
 * sync with it.
 *
 * Idempotency contract: a document whose chunk count, per-chunk content hashes
 * and embedding model all match what is stored is skipped; anything else is
 * re-chunked, re-embedded and REPLACED wholesale (delete + insert per source),
 * because re-chunking can shift chunk boundaries and leave orphaned indexes
 * behind. `force = true` skips the match check entirely — the repair path for
 * a lost or swapped derived engine.
 *
 * Throughput contract: writes are issued PER PAGE, not per document. The
 * Qdrant engine costs two HTTP round trips per call, so per-document writes
 * capped indexing at ~30 documents/second; page-batching removes that ceiling.
 * The canonical SQL store serializes its own writes (deadlock avoidance), and
 * embedding — CPU-bound — still runs across [workers] coroutines.
 */
class KnowledgeIndexer(
    private val repository: RagRepository,
    private val embedder: EmbeddingProvider,
    private val canonicalStore: VectorStore,
    private val searchEngine: VectorStore,
    private val chunker: Chunker,
    private val workers: Int = DEFAULT_WORKERS,
) {
    private val running = AtomicBoolean(false)

    /** Single-flight guard: a second concurrent reindex is rejected, not queued. */
    fun tryBegin(): Boolean = running.compareAndSet(false, true)

    fun end() {
        running.set(false)
    }

    suspend fun indexCorpus(force: Boolean = false): IndexReport =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            var scanned = 0L
            var written = 0L
            var unchanged = 0L
            val semaphore = Semaphore(workers)
            for ((languageId, _) in repository.languages()) {
                for (sourceType in SOURCE_ORDER) {
                    var afterId = 0L
                    var morePages = true
                    while (morePages) {
                        val batch = readPage(sourceType, languageId, afterId, PAGE_SIZE)
                        if (batch.isEmpty()) {
                            morePages = false
                        } else {
                            afterId = batch.last().sourceId
                            val built: List<Built> =
                                coroutineScope {
                                    batch
                                        .map { doc ->
                                            async {
                                                semaphore.withPermit { buildEntries(doc, force) }
                                            }
                                        }.awaitAll()
                                }
                            val changed = built.filterIsInstance<Built.Changed>()
                            val writes =
                                changed.map { changedDoc ->
                                    SourceWrite(
                                        sourceType = sourceType,
                                        sourceId = changedDoc.doc.sourceId,
                                        languageId = changedDoc.doc.languageId,
                                        entries = changedDoc.entries,
                                    )
                                }
                            if (writes.isNotEmpty()) {
                                canonicalStore.writeSources(writes)
                                if (searchEngine !== canonicalStore) {
                                    searchEngine.writeSources(writes)
                                }
                            }
                            scanned += batch.size
                            written += writes.sumOf { it.entries.size }
                            unchanged += built.size - writes.size
                            morePages = batch.size == PAGE_SIZE
                        }
                    }
                }
            }
            IndexReport(
                documentsScanned = scanned,
                chunksWritten = written,
                documentsUnchanged = unchanged,
                durationMs = System.currentTimeMillis() - startedAt,
            )
        }

    private fun readPage(
        sourceType: String,
        languageId: Long,
        afterId: Long,
        limit: Int,
    ): List<CorpusDoc> =
        when (sourceType) {
            RagRepository.SOURCE_VOCABULARY -> repository.vocabularyPage(languageId, afterId, limit)
            RagRepository.SOURCE_GRAMMAR -> repository.grammarPage(languageId, afterId, limit)
            else -> repository.lessonsPage(languageId, afterId, limit)
        }

    /** Returns the store-ready entries, or null when the doc is unchanged. */
    private suspend fun buildEntries(
        doc: CorpusDoc,
        force: Boolean,
    ): Built {
        val pieces = chunker.chunk(doc.body)
        val hashes = pieces.map { VectorMath.sha256Hex(it) }
        val existing =
            if (force) {
                emptyList()
            } else {
                repository.chunkIdentities(doc.sourceType, doc.sourceId)
            }
        val matches =
            existing.size == pieces.size &&
                hashes.indices.all { i ->
                    existing[i].contentHash == hashes[i] && existing[i].embeddingModel == embedder.model
                }
        if (matches) return Built.Unchanged(doc)

        val vectors = embedder.embed(pieces)
        val entries =
            pieces.mapIndexed { index, piece ->
                ChunkEntry(
                    chunkIndex = index,
                    payload =
                        ChunkPayload(
                            title = doc.title.take(MAX_TITLE),
                            content = piece,
                            level = doc.level,
                        ),
                    embeddingModel = embedder.model,
                    contentHash = hashes[index],
                    embedding = vectors[index],
                )
            }
        return Built.Changed(doc, entries)
    }

    private sealed interface Built {
        val doc: CorpusDoc

        data class Changed(
            override val doc: CorpusDoc,
            val entries: List<ChunkEntry>,
        ) : Built

        data class Unchanged(
            override val doc: CorpusDoc,
        ) : Built
    }

    private companion object {
        const val DEFAULT_WORKERS = 4
        const val PAGE_SIZE = 2000

        /** knowledge_chunks.title is VARCHAR(300); indexer clips defensively. */
        const val MAX_TITLE = 300
        val SOURCE_ORDER =
            listOf(
                RagRepository.SOURCE_VOCABULARY,
                RagRepository.SOURCE_GRAMMAR,
                RagRepository.SOURCE_LESSON,
            )
    }
}

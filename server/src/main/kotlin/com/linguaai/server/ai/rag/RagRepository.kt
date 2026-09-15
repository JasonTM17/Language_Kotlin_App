package com.linguaai.server.ai.rag

import com.linguaai.server.db.GrammarLessons
import com.linguaai.server.db.KnowledgeChunks
import com.linguaai.server.db.Languages
import com.linguaai.server.db.Lessons
import com.linguaai.server.db.Vocabularies
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/** A corpus document normalized across the three source tables. */
class CorpusDoc(
    val sourceType: String,
    val sourceId: Long,
    val languageId: Long,
    val level: String?,
    val title: String,
    val body: String,
)

class CorpusCounts(
    val vocabularies: Long,
    val grammarLessons: Long,
    val lessons: Long,
)

class ChunkIdentity(
    val chunkIndex: Int,
    val contentHash: String,
    val embeddingModel: String,
)

/**
 * Keyset-paginated corpus readers plus canonical chunk persistence for the
 * RAG pipeline. Keyset (`id > ?`) rather than OFFSET paging: at 100k+ rows an
 * OFFSET scan is quadratic, and this indexer walks the whole corpus.
 */
class RagRepository {
    fun languages(): List<Pair<Long, String>> =
        transaction {
            Languages.selectAll().orderBy(Languages.id, SortOrder.ASC).map {
                it[Languages.id] to it[Languages.levels]
            }
        }

    fun vocabularyPage(
        languageId: Long,
        afterId: Long,
        limit: Int,
    ): List<CorpusDoc> =
        transaction {
            Vocabularies
                .selectAll()
                .andWhere { (Vocabularies.languageId eq languageId) and (Vocabularies.id greater afterId) }
                .orderBy(Vocabularies.id, SortOrder.ASC)
                .limit(limit)
                .map { row ->
                    CorpusDoc(
                        sourceType = SOURCE_VOCABULARY,
                        sourceId = row[Vocabularies.id],
                        languageId = languageId,
                        level = row[Vocabularies.level],
                        title =
                            buildString {
                                append(row[Vocabularies.word])
                                row[Vocabularies.reading]?.let { append(" ($it)") }
                                append(" — ")
                                append(row[Vocabularies.meaning])
                            },
                        body =
                            buildString {
                                append("Word: ${row[Vocabularies.word]}\n")
                                row[Vocabularies.reading]?.let { append("Reading: $it\n") }
                                row[Vocabularies.pronunciation]?.let { append("Pronunciation: $it\n") }
                                append("Meaning: ${row[Vocabularies.meaning]}\n")
                                row[Vocabularies.example]?.let { append("Example: $it\n") }
                                row[Vocabularies.exampleTranslation]?.let { append("Example translation: $it\n") }
                                row[Vocabularies.category]?.let { append("Category: $it") }
                            },
                    )
                }
        }

    fun grammarPage(
        languageId: Long,
        afterId: Long,
        limit: Int,
    ): List<CorpusDoc> =
        transaction {
            GrammarLessons
                .selectAll()
                .andWhere { (GrammarLessons.languageId eq languageId) and (GrammarLessons.id greater afterId) }
                .orderBy(GrammarLessons.id, SortOrder.ASC)
                .limit(limit)
                .map { row ->
                    CorpusDoc(
                        sourceType = SOURCE_GRAMMAR,
                        sourceId = row[GrammarLessons.id],
                        languageId = languageId,
                        level = row[GrammarLessons.level],
                        title = row[GrammarLessons.title],
                        body =
                            buildString {
                                row[GrammarLessons.structure]?.let { append("Structure: $it\n") }
                                row[GrammarLessons.meaning]?.let { append("Meaning: $it\n") }
                                row[GrammarLessons.usageNotes]?.let { append("Usage: $it\n") }
                                row[GrammarLessons.examples]?.let { append("Examples: $it\n") }
                                row[GrammarLessons.notes]?.let { append("Notes: $it") }
                            },
                    )
                }
        }

    fun lessonsPage(
        languageId: Long,
        afterId: Long,
        limit: Int,
    ): List<CorpusDoc> =
        transaction {
            Lessons
                .selectAll()
                .andWhere { (Lessons.languageId eq languageId) and (Lessons.id greater afterId) }
                .orderBy(Lessons.id, SortOrder.ASC)
                .limit(limit)
                .map { row ->
                    CorpusDoc(
                        sourceType = SOURCE_LESSON,
                        sourceId = row[Lessons.id],
                        languageId = languageId,
                        level = row[Lessons.level],
                        title = row[Lessons.title],
                        body =
                            buildString {
                                row[Lessons.description]?.let { append("${it}\n") }
                                row[Lessons.content]?.let { append(it) }
                            },
                    )
                }
        }

    /** Existing chunk identities for one source; drives skip/replace decisions. */
    fun chunkIdentities(
        sourceType: String,
        sourceId: Long,
    ): List<ChunkIdentity> =
        transaction {
            KnowledgeChunks
                .selectAll()
                .andWhere { (KnowledgeChunks.sourceType eq sourceType) and (KnowledgeChunks.sourceId eq sourceId) }
                .orderBy(KnowledgeChunks.chunkIndex, SortOrder.ASC)
                .map {
                    ChunkIdentity(
                        chunkIndex = it[KnowledgeChunks.chunkIndex],
                        contentHash = it[KnowledgeChunks.contentHash],
                        embeddingModel = it[KnowledgeChunks.embeddingModel],
                    )
                }
        }

    fun countChunks(): Long = transaction { KnowledgeChunks.selectAll().count() }

    fun corpusCounts(): CorpusCounts =
        transaction {
            CorpusCounts(
                vocabularies = Vocabularies.selectAll().count(),
                grammarLessons = GrammarLessons.selectAll().count(),
                lessons = Lessons.selectAll().count(),
            )
        }

    /** Deletes every chunk belonging to [sourceIds]; used by seed teardown. */
    fun deleteChunksFor(
        sourceType: String,
        sourceIds: Collection<Long>,
    ) {
        if (sourceIds.isEmpty()) return
        transaction {
            for (id in sourceIds) {
                KnowledgeChunks.deleteWhere {
                    (KnowledgeChunks.sourceType eq sourceType) and (KnowledgeChunks.sourceId eq id)
                }
            }
        }
    }

    companion object {
        const val SOURCE_VOCABULARY = "VOCABULARY"
        const val SOURCE_GRAMMAR = "GRAMMAR"
        const val SOURCE_LESSON = "LESSON"
    }
}

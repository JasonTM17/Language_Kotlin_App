package com.linguaai.server.ops

import com.linguaai.server.db.GrammarLessons
import com.linguaai.server.db.KnowledgeChunks
import com.linguaai.server.db.Lessons
import com.linguaai.server.db.Vocabularies
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

/** One synthetic vocabulary row, pre-generated so inserts can be batched. */
class SyntheticVocabulary(
    val level: String,
    val word: String,
    val pronunciation: String,
    val meaning: String,
    val example: String,
    val exampleTranslation: String,
)

class SyntheticGrammar(
    val level: String,
    val title: String,
    val body: GrammarBody,
    val difficulty: Int,
)

class GrammarBody(
    val structure: String,
    val meaning: String,
    val usageNotes: String,
    val examples: String,
)

class SyntheticLesson(
    val level: String,
    val title: String,
    val description: String,
    val content: String,
    val estimatedMinutes: Int,
    val difficulty: Int,
)

class PurgeReport(
    val vocabulariesDeleted: Long,
    val grammarDeleted: Long,
    val lessonsDeleted: Long,
    val chunksDeleted: Long,
    /** Source ids removed, per type — the derived engine cleans by identity. */
    val ids: PurgedIds,
)

class PurgedIds(
    val vocabularyIds: List<Long>,
    val grammarIds: List<Long>,
    val lessonIds: List<Long>,
)

/**
 * Persistence for the deterministic scale seeder. All writes are batched
 * ([BATCH_SIZE] rows per transaction) — one giant transaction of 100k+ rows
 * would exhaust the Hikari pool and MySQL undo logs (Kongming fix 6).
 */
class SeedRepository {
    fun vocabularyCount(languageId: Long): Long =
        transaction { Vocabularies.selectAll().where { Vocabularies.languageId eq languageId }.count() }

    fun insertVocabulary(
        languageId: Long,
        rows: List<SyntheticVocabulary>,
    ) {
        val now = LocalDateTime.now()
        rows.chunked(BATCH_SIZE).forEach { batch ->
            transaction {
                Vocabularies.batchInsert(batch) { row ->
                    this[Vocabularies.languageId] = languageId
                    this[Vocabularies.level] = row.level
                    this[Vocabularies.word] = row.word
                    this[Vocabularies.reading] = null
                    this[Vocabularies.pronunciation] = row.pronunciation
                    this[Vocabularies.meaning] = row.meaning
                    this[Vocabularies.example] = row.example
                    this[Vocabularies.exampleTranslation] = row.exampleTranslation
                    this[Vocabularies.category] = SYNTHETIC_CATEGORY
                    this[Vocabularies.createdAt] = now
                }
            }
        }
    }

    fun insertGrammar(
        languageId: Long,
        rows: List<SyntheticGrammar>,
    ) {
        val now = LocalDateTime.now()
        rows.chunked(BATCH_SIZE).forEach { batch ->
            transaction {
                GrammarLessons.batchInsert(batch) { row ->
                    this[GrammarLessons.languageId] = languageId
                    this[GrammarLessons.level] = row.level
                    this[GrammarLessons.title] = row.title
                    this[GrammarLessons.structure] = row.body.structure
                    this[GrammarLessons.meaning] = row.body.meaning
                    this[GrammarLessons.usageNotes] = row.body.usageNotes
                    this[GrammarLessons.examples] = row.body.examples
                    this[GrammarLessons.notes] = null
                    this[GrammarLessons.difficulty] = row.difficulty
                    this[GrammarLessons.createdAt] = now
                }
            }
        }
    }

    fun insertLesson(
        languageId: Long,
        rows: List<SyntheticLesson>,
    ) {
        val now = LocalDateTime.now()
        rows.chunked(BATCH_SIZE).forEach { batch ->
            transaction {
                Lessons.batchInsert(batch) { row ->
                    this[Lessons.languageId] = languageId
                    this[Lessons.level] = row.level
                    this[Lessons.title] = row.title
                    this[Lessons.description] = row.description
                    this[Lessons.type] = SYNTHETIC_LESSON_TYPE
                    this[Lessons.estimatedMinutes] = row.estimatedMinutes
                    this[Lessons.difficulty] = row.difficulty
                    this[Lessons.content] = row.content
                    this[Lessons.createdAt] = now
                    this[Lessons.updatedAt] = now
                }
            }
        }
    }

    /** Deletes synthetic corpus rows and returns their ids for chunk cleanup. */
    fun purgeSynthetic(): PurgeReport =
        transaction {
            val vocabIds =
                Vocabularies
                    .selectAll()
                    .where { Vocabularies.category eq SYNTHETIC_CATEGORY }
                    .map { it[Vocabularies.id] }
            val grammarIds =
                GrammarLessons
                    .selectAll()
                    .where { syntheticTitle() }
                    .map { it[GrammarLessons.id] }
            val lessonIds =
                Lessons
                    .selectAll()
                    .where { syntheticTitleLessons() }
                    .map { it[Lessons.id] }

            val chunksDeleted =
                countChunksFor(RagSourceTypes.VOCABULARY, vocabIds) +
                    countChunksFor(RagSourceTypes.GRAMMAR, grammarIds) +
                    countChunksFor(RagSourceTypes.LESSON, lessonIds)

            deleteChunksForIds(RagSourceTypes.VOCABULARY, vocabIds)
            deleteChunksForIds(RagSourceTypes.GRAMMAR, grammarIds)
            deleteChunksForIds(RagSourceTypes.LESSON, lessonIds)

            if (vocabIds.isNotEmpty()) {
                Vocabularies.deleteWhere { Vocabularies.category eq SYNTHETIC_CATEGORY }
            }
            if (grammarIds.isNotEmpty()) {
                GrammarLessons.deleteWhere { syntheticTitle() }
            }
            if (lessonIds.isNotEmpty()) {
                Lessons.deleteWhere { syntheticTitleLessons() }
            }
            PurgeReport(
                vocabulariesDeleted = vocabIds.size.toLong(),
                grammarDeleted = grammarIds.size.toLong(),
                lessonsDeleted = lessonIds.size.toLong(),
                chunksDeleted = chunksDeleted,
                ids =
                    PurgedIds(
                        vocabularyIds = vocabIds,
                        grammarIds = grammarIds,
                        lessonIds = lessonIds,
                    ),
            )
        }

    private fun countChunksFor(
        sourceType: String,
        ids: List<Long>,
    ): Long =
        if (ids.isEmpty()) {
            0L
        } else {
            ids.chunked(BATCH_SIZE).sumOf { chunk ->
                KnowledgeChunks
                    .selectAll()
                    .where { chunkFilter(sourceType, chunk) }
                    .count()
            }
        }

    private fun deleteChunksForIds(
        sourceType: String,
        ids: List<Long>,
    ) {
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            KnowledgeChunks.deleteWhere { chunkFilter(sourceType, chunk) }
        }
    }

    private fun chunkFilter(
        sourceType: String,
        ids: List<Long>,
    ) = (KnowledgeChunks.sourceType eq sourceType) and (KnowledgeChunks.sourceId inList ids)

    private fun syntheticTitle(): org.jetbrains.exposed.sql.Op<Boolean> =
        org.jetbrains.exposed.sql.Op
            .build { GrammarLessons.title like "$SYNTHETIC_PREFIX%" }

    private fun syntheticTitleLessons(): org.jetbrains.exposed.sql.Op<Boolean> =
        org.jetbrains.exposed.sql.Op
            .build { Lessons.title like "$SYNTHETIC_PREFIX%" }

    companion object {
        const val BATCH_SIZE = 500
        const val SYNTHETIC_CATEGORY = "SYNTHETIC"
        const val SYNTHETIC_PREFIX = "[SYN]"
        const val SYNTHETIC_LESSON_TYPE = "vocabulary"
    }
}

/** Source-type vocabulary shared with the RAG package without coupling. */
object RagSourceTypes {
    const val VOCABULARY = "VOCABULARY"
    const val GRAMMAR = "GRAMMAR"
    const val LESSON = "LESSON"
}

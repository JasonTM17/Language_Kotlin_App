package com.linguaai.server.ops

import com.linguaai.server.ai.rag.RagRepository
import com.linguaai.server.ai.rag.VectorStore
import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class SeedReportDto(
    val languagesCovered: Int,
    val vocabulariesInserted: Long,
    val grammarInserted: Long,
    val lessonsInserted: Long,
)

@Serializable
data class PurgeReportDto(
    val vocabulariesDeleted: Long,
    val grammarDeleted: Long,
    val lessonsDeleted: Long,
    val chunksDeleted: Long,
)

class SeedInProgressException : RuntimeException("A seed or reindex operation is already running")

/**
 * Deterministic corpus generator for scale validation (ADR-007: "100k-vector
 * scale validation, single-node compose", not production content).
 *
 * Everything is derived from row indexes — no RNG — so a given request always
 * produces the same corpus on any machine. Synthetic rows are marked
 * (category SYNTHETIC / [SYN] title prefix) so the purge endpoint can remove
 * exactly what the seeder created, keeping dev machines reversible.
 */
class OpsService(
    private val ragRepository: RagRepository,
    private val seedRepository: SeedRepository,
    private val canonicalStore: VectorStore,
    private val searchEngine: VectorStore,
    private val guard: OpsSingleFlight,
) {
    fun seedScale(
        wordsPerLanguage: Int,
        grammarPerLanguage: Int,
        lessonsPerLanguage: Int,
    ): SeedReportDto {
        validateRequest(wordsPerLanguage, grammarPerLanguage, lessonsPerLanguage)
        if (!guard.tryBegin()) throw SeedInProgressException()
        try {
            val languages = ragRepository.languages()
            var vocabInserted = 0L
            var grammarInserted = 0L
            var lessonsInserted = 0L
            for ((languageId, levelsCsv) in languages) {
                val levels = splitLevels(levelsCsv)
                val existing = seedRepository.vocabularyCount(languageId).toInt()
                val words =
                    generateVocabulary(from = existing, count = wordsPerLanguage, levels = levels)
                seedRepository.insertVocabulary(languageId, words)
                vocabInserted += words.size
                val grammar =
                    generateGrammar(from = existing, count = grammarPerLanguage, levels = levels)
                seedRepository.insertGrammar(languageId, grammar)
                grammarInserted += grammar.size
                val lessons =
                    generateLessons(from = existing, count = lessonsPerLanguage, levels = levels)
                seedRepository.insertLesson(languageId, lessons)
                lessonsInserted += lessons.size
            }
            return SeedReportDto(
                languagesCovered = languages.size,
                vocabulariesInserted = vocabInserted,
                grammarInserted = grammarInserted,
                lessonsInserted = lessonsInserted,
            )
        } finally {
            guard.end()
        }
    }

    suspend fun purge(): PurgeReportDto {
        // Purge mutates the corpus and the derived engine, so it belongs under
        // the same exclusion gate as seed and reindex.
        if (!guard.tryBegin()) throw SeedInProgressException()
        try {
            val report = seedRepository.purgeSynthetic()
            // The canonical store cleaned its own rows inside purgeSynthetic;
            // the derived engine must be cleaned by identity too, or purged
            // content stays retrievable from vectors alone.
            if (searchEngine !== canonicalStore) {
                searchEngine.deleteSources(RagSourceTypes.VOCABULARY, report.ids.vocabularyIds)
                searchEngine.deleteSources(RagSourceTypes.GRAMMAR, report.ids.grammarIds)
                searchEngine.deleteSources(RagSourceTypes.LESSON, report.ids.lessonIds)
            }
            return PurgeReportDto(
                vocabulariesDeleted = report.vocabulariesDeleted,
                grammarDeleted = report.grammarDeleted,
                lessonsDeleted = report.lessonsDeleted,
                chunksDeleted = report.chunksDeleted,
            )
        } finally {
            guard.end()
        }
    }

    private fun validateRequest(
        wordsPerLanguage: Int,
        grammarPerLanguage: Int,
        lessonsPerLanguage: Int,
    ) {
        if (wordsPerLanguage < 0 || grammarPerLanguage < 0 || lessonsPerLanguage < 0) {
            throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.VALIDATION, "Counts must be non-negative")
        }
        if (wordsPerLanguage > MAX_WORDS_PER_LANGUAGE ||
            grammarPerLanguage > MAX_GRAMMAR_PER_LANGUAGE ||
            lessonsPerLanguage > MAX_LESSONS_PER_LANGUAGE
        ) {
            throw ApiException(
                HttpStatusCode.BadRequest,
                ErrorCodes.VALIDATION,
                "Caps exceeded: max $MAX_WORDS_PER_LANGUAGE words, " +
                    "$MAX_GRAMMAR_PER_LANGUAGE grammar, $MAX_LESSONS_PER_LANGUAGE lessons per language",
            )
        }
    }

    private fun splitLevels(levelsCsv: String): List<String> =
        levelsCsv
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("A1") }

    private fun generateVocabulary(
        from: Int,
        count: Int,
        levels: List<String>,
    ): List<SyntheticVocabulary> =
        (0 until count).map { offset ->
            val index = from + offset
            val word = WordFactory.word(index)
            SyntheticVocabulary(
                level = levels[index % levels.size],
                word = word,
                pronunciation = word,
                meaning = "Synthetic term ${index + 1}: ${WordFactory.gloss(index)}",
                example = "This is a synthetic example sentence ${index + 1} using $word.",
                exampleTranslation = "Đây là câu ví dụ tổng hợp ${index + 1} dùng $word.",
            )
        }

    private fun generateGrammar(
        from: Int,
        count: Int,
        levels: List<String>,
    ): List<SyntheticGrammar> =
        (0 until count).map { offset ->
            val index = from + offset
            val word = WordFactory.word(index)
            SyntheticGrammar(
                level = levels[index % levels.size],
                title = "$SYNTHETIC_TITLE_PREFIX Pattern ${index + 1}: the $word construction",
                body =
                    GrammarBody(
                        structure = "A + connector + $word",
                        meaning = "Synthetic pattern ${index + 1}: links a topic to ${WordFactory.gloss(index)}.",
                        usageNotes = "Deterministic drill text for retrieval scale testing.",
                        examples = "Example ${index + 1} uses $word with a level-appropriate connector.",
                    ),
                difficulty = MIN_DIFFICULTY + (index % DIFFICULTY_LEVELS),
            )
        }

    private fun generateLessons(
        from: Int,
        count: Int,
        levels: List<String>,
    ): List<SyntheticLesson> =
        (0 until count).map { offset ->
            val index = from + offset
            val first = WordFactory.word(index)
            val second = WordFactory.word(index + SECOND_WORD_OFFSET)
            val third = WordFactory.word(index + THIRD_WORD_OFFSET)
            SyntheticLesson(
                level = levels[index % levels.size],
                title = "$SYNTHETIC_TITLE_PREFIX Lesson ${index + 1}: meeting $first",
                description = "Synthetic lesson ${index + 1} introduces $first, $second and $third.",
                content =
                    "Lesson ${index + 1} teaches $first in context. " +
                        "It contrasts $first with $second, then practises $third. " +
                        WordFactory.gloss(index) + " is the theme of the closing dialogue.",
                estimatedMinutes = MIN_ESTIMATED_MINUTES + (index % ESTIMATED_MINUTES_RANGE),
                difficulty = MIN_DIFFICULTY + (index % DIFFICULTY_LEVELS),
            )
        }

    private companion object {
        const val MAX_WORDS_PER_LANGUAGE = 50_000
        const val MAX_GRAMMAR_PER_LANGUAGE = 10_000
        const val MAX_LESSONS_PER_LANGUAGE = 5_000
        const val SYNTHETIC_TITLE_PREFIX = "[SYN]"
        const val MIN_DIFFICULTY = 1
        const val DIFFICULTY_LEVELS = 3
        const val MIN_ESTIMATED_MINUTES = 5
        const val ESTIMATED_MINUTES_RANGE = 20

        /** Large coprime offsets spread companion words away from the base index. */
        const val SECOND_WORD_OFFSET = 1_000_003
        const val THIRD_WORD_OFFSET = 2_000_017
    }
}

/** Deterministic pseudo-words: syllable math instead of an RNG. */
private object WordFactory {
    private val ONSET = listOf("ka", "mo", "ri", "sa", "to", "nu", "he", "yu", "wa", "ko", "me", "ta")
    private val CODA = listOf("ran", "ki", "so", "mar", "ten", "no", "shi", "ba", "lu", "ken", "do", "mi")
    private val GLOSSES =
        listOf(
            "greetings",
            "numbers",
            "family",
            "weather",
            "travel",
            "food",
            "work",
            "school",
            "health",
            "hobbies",
            "shopping",
            "directions",
            "time",
            "feelings",
            "nature",
            "city life",
        )

    /**
     * The syllable cycle repeats after ~1,728 combinations, but vocabularies
     * carry a UNIQUE(language_id, word) constraint (Flyway V7) and scale seeds
     * request tens of thousands of words per language — so the stable index is
     * part of the word itself. Uniqueness is structural, not probabilistic.
     */
    fun word(index: Int): String =
        ONSET[index % ONSET.size] + CODA[(index / ONSET.size) % CODA.size] +
            ONSET[(index / (ONSET.size * CODA.size)) % ONSET.size] + "-" + (index + 1)

    fun gloss(index: Int): String = GLOSSES[index % GLOSSES.size]
}

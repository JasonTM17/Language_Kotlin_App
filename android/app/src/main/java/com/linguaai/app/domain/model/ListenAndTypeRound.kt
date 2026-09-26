package com.linguaai.app.domain.model

import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random

/** In-memory state for one bounded listen-and-type round. */
class ListenAndTypeRound private constructor(
    val words: List<VocabularyCard>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val answeredCorrectly: Boolean? = null,
) {
    val currentWord: VocabularyCard?
        get() = words.getOrNull(currentIndex)

    val isEmpty: Boolean
        get() = words.isEmpty()

    val isComplete: Boolean
        get() = currentIndex >= words.size

    val hasAnswered: Boolean
        get() = answeredCorrectly != null

    fun submitAnswer(answer: String): ListenAndTypeRound {
        val word = currentWord ?: return this
        if (hasAnswered) return this

        val isCorrect = answersMatch(word.word, answer)
        return withState(
            newCorrectCount = correctCount + if (isCorrect) 1 else 0,
            newAnsweredCorrectly = isCorrect,
        )
    }

    fun advance(): ListenAndTypeRound {
        if (!hasAnswered || isComplete) return this
        return withState(newCurrentIndex = currentIndex + 1, newAnsweredCorrectly = null)
    }

    fun retry(random: Random = Random.Default): ListenAndTypeRound =
        ListenAndTypeRound(
            words = words.shuffled(random),
            currentIndex = 0,
            correctCount = 0,
            answeredCorrectly = null,
        )

    private fun withState(
        newCurrentIndex: Int = currentIndex,
        newCorrectCount: Int = correctCount,
        newAnsweredCorrectly: Boolean? = answeredCorrectly,
    ): ListenAndTypeRound =
        ListenAndTypeRound(
            words = words,
            currentIndex = newCurrentIndex,
            correctCount = newCorrectCount,
            answeredCorrectly = newAnsweredCorrectly,
        )

    companion object {
        const val MAX_WORDS = 10

        fun start(
            languageId: Long,
            candidates: List<VocabularyCard>,
            random: Random = Random.Default,
        ): ListenAndTypeRound {
            val selected =
                candidates
                    .asSequence()
                    .filter { it.languageId == languageId }
                    .filter { normalizeAnswer(it.word).isNotEmpty() }
                    .distinctBy { it.id }
                    .distinctBy { normalizeAnswer(it.word) }
                    .take(MAX_WORDS)
                    .toList()
                    .shuffled(random)
            return ListenAndTypeRound(words = selected)
        }

        fun answersMatch(
            expected: String,
            actual: String,
        ): Boolean {
            val normalizedExpected = normalizeAnswer(expected)
            return normalizedExpected.isNotEmpty() && normalizedExpected == normalizeAnswer(actual)
        }

        private fun normalizeAnswer(value: String): String {
            val composed = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
            val lowercased = composed.lowercase(Locale.ROOT)
            return Normalizer.normalize(lowercased, Normalizer.Form.NFC)
        }
    }
}

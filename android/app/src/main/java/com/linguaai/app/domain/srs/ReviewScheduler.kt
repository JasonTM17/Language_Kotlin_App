package com.linguaai.app.domain.srs

import javax.inject.Inject

/**
 * Pluggable review scheduler. The default implementation is a light SM-2
 * derivative; swap via DI without touching UI or repository code.
 */
interface ReviewScheduler {
    /** Minutes until the next review given the learner's grade. */
    fun nextIntervalMinutes(masteryLevel: Int, grade: ReviewGrade): Long
    /** New mastery level after a review (bounded 0..5). */
    fun nextMastery(masteryLevel: Int, grade: ReviewGrade): Int
}

enum class ReviewGrade { AGAIN, HARD, GOOD, EASY }

/**
 * Light SM-2: again resets progress, hard repeats shortly, good/easy grow the
 * interval geometrically with mastery.
 */
class Sm2LiteScheduler @Inject constructor() : ReviewScheduler {

    override fun nextIntervalMinutes(masteryLevel: Int, grade: ReviewGrade): Long {
        val level = masteryLevel.coerceIn(0, 5)
        return when (grade) {
            ReviewGrade.AGAIN -> AGAIN_MINUTES
            ReviewGrade.HARD -> (BASE_MINUTES * level).coerceAtLeast(HARD_MINUTES)
            ReviewGrade.GOOD -> (BASE_MINUTES * (level + 1).toDouble() * 1.5).toLong()
            ReviewGrade.EASY -> (BASE_MINUTES * (level + 2).toDouble() * 2.0).toLong()
        }
    }

    override fun nextMastery(masteryLevel: Int, grade: ReviewGrade): Int {
        val delta = when (grade) {
            ReviewGrade.AGAIN -> -1
            ReviewGrade.HARD -> 0
            ReviewGrade.GOOD -> 1
            ReviewGrade.EASY -> 2
        }
        return (masteryLevel + delta).coerceIn(0, 5)
    }

    private companion object {
        const val AGAIN_MINUTES = 1L
        const val HARD_MINUTES = 10L
        const val BASE_MINUTES = 60L
    }
}

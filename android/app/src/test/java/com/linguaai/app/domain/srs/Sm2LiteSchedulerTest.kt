package com.linguaai.app.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spaced-repetition interval and mastery progression.
 *
 * These are the rules the whole flashcard schedule rests on, and they are pure
 * functions, so they are cheap to pin down precisely. The cases chosen are the
 * ones where a plausible-looking implementation goes wrong: a floor that is not
 * actually enforced, intervals that stop growing, and mastery escaping its range.
 */
class Sm2LiteSchedulerTest {
    private val scheduler = Sm2LiteScheduler()

    @Test
    fun `again resets the interval to one minute regardless of mastery`() {
        for (mastery in 0..5) {
            assertEquals(
                "AGAIN at mastery $mastery should reset to 1 minute",
                1L,
                scheduler.nextIntervalMinutes(mastery, ReviewGrade.AGAIN),
            )
        }
    }

    @Test
    fun `hard never drops below the ten minute floor`() {
        // At mastery 0 the raw formula (60 * 0) yields 0, which would schedule an
        // immediate re-review. The floor is what prevents that. It binds only at
        // mastery 0 — from mastery 1 the interval is already 60 minutes.
        assertEquals(10L, scheduler.nextIntervalMinutes(0, ReviewGrade.HARD))
        assertEquals(60L, scheduler.nextIntervalMinutes(1, ReviewGrade.HARD))
        for (mastery in 0..5) {
            assertTrue(
                "HARD at mastery $mastery fell below the floor",
                scheduler.nextIntervalMinutes(mastery, ReviewGrade.HARD) >= 10L,
            )
        }
    }

    @Test
    fun `intervals grow with mastery for good and easy`() {
        for (grade in listOf(ReviewGrade.GOOD, ReviewGrade.EASY)) {
            val intervals = (0..5).map { scheduler.nextIntervalMinutes(it, grade) }
            assertEquals(
                "$grade intervals should be non-decreasing as mastery rises",
                intervals.sorted(),
                intervals,
            )
            assertTrue(
                "$grade should produce a longer interval at higher mastery",
                intervals.last() > intervals.first(),
            )
        }
    }

    @Test
    fun `easy schedules further out than good at the same mastery`() {
        for (mastery in 0..5) {
            assertTrue(
                "EASY should outlast GOOD at mastery $mastery",
                scheduler.nextIntervalMinutes(mastery, ReviewGrade.EASY) >
                    scheduler.nextIntervalMinutes(mastery, ReviewGrade.GOOD),
            )
        }
    }

    @Test
    fun `mastery moves in the expected direction per grade`() {
        // AGAIN is the only grade that regresses mastery.
        assertEquals(1, scheduler.nextMastery(2, ReviewGrade.AGAIN))
        assertEquals(2, scheduler.nextMastery(2, ReviewGrade.HARD))
        assertEquals(3, scheduler.nextMastery(2, ReviewGrade.GOOD))
        assertEquals(4, scheduler.nextMastery(2, ReviewGrade.EASY))
    }

    @Test
    fun `mastery is clamped to the zero to five range`() {
        // Lower bound: AGAIN at 0 must not go negative.
        assertEquals(0, scheduler.nextMastery(0, ReviewGrade.AGAIN))
        // Upper bound: EASY at the ceiling must not exceed 5.
        assertEquals(5, scheduler.nextMastery(5, ReviewGrade.EASY))
        assertEquals(5, scheduler.nextMastery(5, ReviewGrade.GOOD))
    }

    @Test
    fun `out of range mastery input is coerced rather than throwing`() {
        // Persisted data could carry a stale or corrupted mastery value; the
        // scheduler must stay total rather than crash the review screen.
        assertEquals(0, scheduler.nextMastery(-5, ReviewGrade.HARD))
        assertEquals(5, scheduler.nextMastery(99, ReviewGrade.HARD))
        assertTrue(scheduler.nextIntervalMinutes(99, ReviewGrade.GOOD) > 0L)
        assertTrue(scheduler.nextIntervalMinutes(-3, ReviewGrade.EASY) > 0L)
    }

    @Test
    fun `every interval is strictly positive`() {
        for (mastery in -2..8) {
            for (grade in ReviewGrade.entries) {
                assertTrue(
                    "mastery=$mastery grade=$grade produced a non-positive interval",
                    scheduler.nextIntervalMinutes(mastery, grade) > 0L,
                )
            }
        }
    }
}

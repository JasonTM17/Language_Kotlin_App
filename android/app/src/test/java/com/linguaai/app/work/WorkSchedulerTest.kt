package com.linguaai.app.work

import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reminder scheduling arithmetic.
 *
 * The reminder is a periodic worker anchored by an initial delay to the next
 * occurrence, so an off-by-one-day error here means the reminder fires a full
 * day late (or immediately) — the kind of bug that is invisible until a user
 * reports "it never reminds me".
 */
class WorkSchedulerTest {

    private val reminder = LocalTime.of(19, 0)

    @Test
    fun `a time still ahead today schedules for today`() {
        val now = LocalDateTime.of(2026, 9, 11, 8, 30)
        assertEquals(
            LocalDateTime.of(2026, 9, 11, 19, 0),
            WorkScheduler.nextOccurrence(reminder, now),
        )
    }

    @Test
    fun `a time already past today schedules for tomorrow`() {
        val now = LocalDateTime.of(2026, 9, 11, 21, 15)
        assertEquals(
            LocalDateTime.of(2026, 9, 12, 19, 0),
            WorkScheduler.nextOccurrence(reminder, now),
        )
    }

    @Test
    fun `exactly now schedules for tomorrow rather than firing immediately`() {
        // isAfter is false when the instants are equal, so this must roll to the
        // next day. Scheduling for "now" would fire a reminder the moment the
        // user saves the setting.
        val now = LocalDateTime.of(2026, 9, 11, 19, 0)
        assertEquals(
            LocalDateTime.of(2026, 9, 12, 19, 0),
            WorkScheduler.nextOccurrence(reminder, now),
        )
    }

    @Test
    fun `crossing midnight lands on the next day`() {
        val now = LocalDateTime.of(2026, 9, 11, 23, 59)
        assertEquals(
            LocalDateTime.of(2026, 9, 12, 19, 0),
            WorkScheduler.nextOccurrence(reminder, now),
        )
    }

    @Test
    fun `crossing a month boundary lands on the correct date`() {
        val now = LocalDateTime.of(2026, 9, 30, 23, 0)
        assertEquals(
            LocalDateTime.of(2026, 10, 1, 19, 0),
            WorkScheduler.nextOccurrence(reminder, now),
        )
    }

    @Test
    fun `an early morning reminder is still today when checked after midnight`() {
        val earlyReminder = LocalTime.of(6, 0)
        val now = LocalDateTime.of(2026, 9, 11, 0, 5)
        assertEquals(
            LocalDateTime.of(2026, 9, 11, 6, 0),
            WorkScheduler.nextOccurrence(earlyReminder, now),
        )
    }
}

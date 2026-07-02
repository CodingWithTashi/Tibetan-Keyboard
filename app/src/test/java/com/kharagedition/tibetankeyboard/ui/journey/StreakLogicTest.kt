package com.kharagedition.tibetankeyboard.ui.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the streak rules that drive the whole Journey habit loop: growth, breakage,
 * idempotency within a day, the grace day for display, at-risk detection, and milestones.
 */
class StreakLogicTest {

    private val day = 20_000L // arbitrary local epoch day

    // ── recordActivity ───────────────────────────────────────────────────────

    @Test
    fun `first ever activity starts a 1-day streak`() {
        val s = StreakLogic.recordActivity(StreakState(), day)
        assertEquals(1, s.lengthDays)
        assertEquals(day, s.lastActiveEpochDay)
        assertEquals(1, s.bestDays)
    }

    @Test
    fun `same-day activity is idempotent`() {
        val once = StreakLogic.recordActivity(StreakState(), day)
        val twice = StreakLogic.recordActivity(once, day)
        assertEquals(once, twice)
    }

    @Test
    fun `consecutive day grows the streak`() {
        var s = StreakLogic.recordActivity(StreakState(), day)
        s = StreakLogic.recordActivity(s, day + 1)
        s = StreakLogic.recordActivity(s, day + 2)
        assertEquals(3, s.lengthDays)
        assertEquals(3, s.bestDays)
    }

    @Test
    fun `missing a day resets the streak to 1 but keeps the best`() {
        var s = StreakState(lengthDays = 10, lastActiveEpochDay = day, bestDays = 10)
        s = StreakLogic.recordActivity(s, day + 2) // skipped day+1
        assertEquals(1, s.lengthDays)
        assertEquals(10, s.bestDays)
    }

    @Test
    fun `best only moves up`() {
        var s = StreakState(lengthDays = 2, lastActiveEpochDay = day, bestDays = 9)
        s = StreakLogic.recordActivity(s, day + 1)
        assertEquals(3, s.lengthDays)
        assertEquals(9, s.bestDays)
        s = s.copy(lengthDays = 9)
        s = StreakLogic.recordActivity(s, day + 2)
        assertEquals(10, s.bestDays)
    }

    // ── displayLength ────────────────────────────────────────────────────────

    @Test
    fun `streak shows through the day after the last active day`() {
        val s = StreakState(lengthDays = 5, lastActiveEpochDay = day, bestDays = 5)
        assertEquals(5, StreakLogic.displayLength(s, day))       // typed today
        assertEquals(5, StreakLogic.displayLength(s, day + 1))   // grace day — still alive
        assertEquals(0, StreakLogic.displayLength(s, day + 2))   // missed a full day — broken
    }

    @Test
    fun `no activity ever shows 0`() {
        assertEquals(0, StreakLogic.displayLength(StreakState(), day))
    }

    // ── isAtRisk ─────────────────────────────────────────────────────────────

    @Test
    fun `at risk only on the grace day`() {
        val s = StreakState(lengthDays = 5, lastActiveEpochDay = day, bestDays = 5)
        assertFalse(StreakLogic.isAtRisk(s, day))        // already typed today
        assertTrue(StreakLogic.isAtRisk(s, day + 1))     // not yet typed today — remind
        assertFalse(StreakLogic.isAtRisk(s, day + 2))    // already broken — nothing to save
        assertFalse(StreakLogic.isAtRisk(StreakState(), day)) // no streak to protect
    }

    // ── milestones ───────────────────────────────────────────────────────────

    @Test
    fun `milestone fires exactly when crossed`() {
        assertEquals(3, StreakLogic.milestoneCrossed(2, 3))
        assertEquals(7, StreakLogic.milestoneCrossed(6, 7))
        assertEquals(108, StreakLogic.milestoneCrossed(107, 108))
        assertNull(StreakLogic.milestoneCrossed(3, 4))   // between milestones
        assertNull(StreakLogic.milestoneCrossed(7, 7))   // no growth
    }

    @Test
    fun `next milestone ahead of the current streak`() {
        assertEquals(3, StreakLogic.nextMilestone(0))
        assertEquals(7, StreakLogic.nextMilestone(3))
        assertEquals(108, StreakLogic.nextMilestone(60))
        assertEquals(365, StreakLogic.nextMilestone(108))
        assertNull(StreakLogic.nextMilestone(365))
    }
}

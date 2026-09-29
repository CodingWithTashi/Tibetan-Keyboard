package com.kharagedition.tibetankeyboard.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionQuotaTest {

    private val today = 20_000L
    private val limit = 20

    private fun acceptN(n: Int, start: QuotaState = QuotaState(today, 0)): QuotaState =
        (1..n).fold(start) { s, _ -> SuggestionQuota.afterAccept(s, today) }

    private fun mode(state: QuotaState, isPremium: Boolean? = false, canSell: Boolean = true, day: Long = today, lim: Int = limit) =
        SuggestionQuota.mode(isPremium, canSell, state, day, lim)

    @Test
    fun freeUser_isLiveUntilTheLimit_thenLocked() {
        val at19 = acceptN(19)
        assertEquals(1, SuggestionQuota.remaining(at19, today, limit))
        assertEquals(SuggestionMode.LIVE, mode(at19))

        val at20 = SuggestionQuota.afterAccept(at19, today)
        assertEquals(0, SuggestionQuota.remaining(at20, today, limit))
        assertEquals(SuggestionMode.LOCKED, mode(at20))
    }

    @Test
    fun premium_isAlwaysLive() {
        assertEquals(SuggestionMode.LIVE, mode(acceptN(100), isPremium = true))
    }

    @Test
    fun unknownPremium_isLive_soASubscriberNeverSeesLocks() {
        assertEquals(SuggestionMode.LIVE, mode(acceptN(100), isPremium = null))
        assertFalse(SuggestionQuota.applies(isPremium = null, canSell = true))
    }

    @Test
    fun whereNothingCanBeSold_isLive() {
        assertEquals(SuggestionMode.LIVE, mode(acceptN(100), canSell = false))
        assertFalse(SuggestionQuota.applies(isPremium = false, canSell = false))
        assertTrue(SuggestionQuota.applies(isPremium = false, canSell = true))
    }

    @Test
    fun usesLastFree_onlyOnTheAcceptThatEmptiesIt() {
        assertFalse(SuggestionQuota.usesLastFree(acceptN(18), today, limit))
        assertTrue(SuggestionQuota.usesLastFree(acceptN(19), today, limit))
        assertFalse(SuggestionQuota.usesLastFree(acceptN(20), today, limit))
        // Limit lowered on the dashboard mid-day: already over it, so nothing "runs out" again.
        assertFalse(SuggestionQuota.usesLastFree(acceptN(10), today, 5))
    }

    @Test
    fun newDay_resetsTheCount() {
        val usedUp = acceptN(20)
        assertEquals(limit, SuggestionQuota.remaining(usedUp, today + 1, limit))
        assertEquals(SuggestionMode.LIVE, mode(usedUp, day = today + 1))
        assertEquals(QuotaState(today + 1, 1), SuggestionQuota.afterAccept(usedUp, today + 1))
    }

    @Test
    fun clockMovedBackwards_startsFresh_ratherThanStayingLocked() {
        val usedUp = acceptN(20)
        assertEquals(limit, SuggestionQuota.remaining(usedUp, today - 1, limit))
    }

    @Test
    fun zeroOrNegativeLimit_locksImmediately() {
        assertEquals(SuggestionMode.LOCKED, mode(QuotaState(today, 0), lim = 0))
        assertEquals(SuggestionMode.LOCKED, mode(QuotaState(today, 0), lim = -3))
        assertEquals(0, SuggestionQuota.remaining(QuotaState(today, 0), today, -3))
    }
}

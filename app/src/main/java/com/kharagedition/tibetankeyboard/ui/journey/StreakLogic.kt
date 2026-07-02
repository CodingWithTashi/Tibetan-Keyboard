package com.kharagedition.tibetankeyboard.ui.journey

/**
 * Pure (Android-free) streak arithmetic for the "Tibetan Journey" feature, kept as plain data
 * so the streak rules are unit-testable without touching SharedPreferences or the IME
 * (same pattern as [com.kharagedition.tibetankeyboard.ui.keyboard.ProStripState]).
 *
 * Days are counted as **local-timezone epoch days** (see TypingStatsStore.todayEpochDay) so the
 * streak rolls over at the user's local midnight, like Duolingo's.
 */
data class StreakState(
    /** Consecutive active days ending at [lastActiveEpochDay]. 0 = never typed. */
    val lengthDays: Int = 0,
    /** Local epoch day of the most recent active day. -1 = never typed. */
    val lastActiveEpochDay: Long = -1L,
    /** All-time longest streak. */
    val bestDays: Int = 0,
)

object StreakLogic {

    /** Celebration milestones. 108 is the mala-bead count — the signature milestone. */
    val MILESTONES = listOf(3, 7, 14, 30, 60, 108, 365)

    /**
     * Fold one day of typing activity into the streak. Idempotent within a day:
     * calling it again on the same [todayEpochDay] returns the state unchanged.
     */
    fun recordActivity(state: StreakState, todayEpochDay: Long): StreakState {
        val grown = when (todayEpochDay - state.lastActiveEpochDay) {
            0L -> return state                    // already counted today
            1L -> state.lengthDays + 1            // consecutive day — streak grows
            else -> 1                             // first day ever, or streak was broken
        }
        return StreakState(
            lengthDays = grown,
            lastActiveEpochDay = todayEpochDay,
            bestDays = maxOf(grown, state.bestDays),
        )
    }

    /**
     * The streak length to show the user. A streak stays *alive* (still shown) through the
     * whole day after the last active day; it reads 0 only once a full day has been missed.
     */
    fun displayLength(state: StreakState, todayEpochDay: Long): Int =
        when (todayEpochDay - state.lastActiveEpochDay) {
            0L, 1L -> state.lengthDays
            else -> 0
        }

    /** True when the user has a live streak but hasn't typed yet today — reminder territory. */
    fun isAtRisk(state: StreakState, todayEpochDay: Long): Boolean =
        state.lengthDays > 0 && todayEpochDay - state.lastActiveEpochDay == 1L

    /** The milestone crossed by growing from [previousDays] to [currentDays], if any. */
    fun milestoneCrossed(previousDays: Int, currentDays: Int): Int? =
        MILESTONES.firstOrNull { previousDays < it && currentDays >= it }

    /** The next milestone ahead of [currentDays] (null once past the last one). */
    fun nextMilestone(currentDays: Int): Int? = MILESTONES.firstOrNull { it > currentDays }
}

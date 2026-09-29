package com.kharagedition.tibetankeyboard.data.local

import android.content.Context
import com.kharagedition.tibetankeyboard.subscription.QuotaState
import com.kharagedition.tibetankeyboard.subscription.SuggestionMode
import com.kharagedition.tibetankeyboard.subscription.SuggestionQuota
import com.kharagedition.tibetankeyboard.util.localEpochDay

/**
 * Persists today's free-suggestion count for [SuggestionQuota]. Used from the IME on the UI thread;
 * the keyboard and the app share one process, so one synchronized instance is enough.
 *
 * The daily limit is passed in by the caller (it comes from RevenueCat offering metadata and can
 * change without a release). Stores only a count and day numbers — never the suggestions.
 */
class SuggestionQuotaStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    private fun state(): QuotaState =
        QuotaState(day = prefs.getLong(KEY_DAY, -1L), used = prefs.getInt(KEY_USED, 0))

    /** The strip's mode. Reads the stored count only when the limit applies (free, sellable). */
    fun mode(isPremium: Boolean?, canSell: Boolean, limit: Int, today: Long = localEpochDay()): SuggestionMode {
        if (!SuggestionQuota.applies(isPremium, canSell)) return SuggestionMode.LIVE
        return SuggestionQuota.mode(isPremium, canSell, state(), today, limit)
    }

    /** Count one accepted suggestion. Returns true if that used up today's last free one. */
    @Synchronized
    fun recordAccept(limit: Int, today: Long = localEpochDay()): Boolean {
        val before = state()
        val next = SuggestionQuota.afterAccept(before, today)
        prefs.edit().putLong(KEY_DAY, next.day).putInt(KEY_USED, next.used).apply()
        return SuggestionQuota.usesLastFree(before, today, limit)
    }

    /**
     * True the first time it's called on [today], false after — so the locked strip's
     * `feature_gate_shown` is logged once a day even though the IME process restarts often.
     */
    @Synchronized
    fun markLockedShownToday(today: Long = localEpochDay()): Boolean {
        if (prefs.getLong(KEY_LOCKED_SHOWN_DAY, -1L) == today) return false
        prefs.edit().putLong(KEY_LOCKED_SHOWN_DAY, today).apply()
        return true
    }

    /** Debug builds only (see `applyDebugMonetizationOverrides`): set today's used count. */
    @Synchronized
    fun debugSetUsedToday(used: Int, today: Long = localEpochDay()) {
        prefs.edit().putLong(KEY_DAY, today).putInt(KEY_USED, used.coerceAtLeast(0)).apply()
    }

    companion object {
        private const val PREFS_NAME = "suggestion_quota"
        private const val KEY_DAY = "day"
        private const val KEY_USED = "used"
        private const val KEY_LOCKED_SHOWN_DAY = "locked_shown_day"

        @Volatile
        private var INSTANCE: SuggestionQuotaStore? = null

        fun getInstance(context: Context): SuggestionQuotaStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SuggestionQuotaStore(context).also { INSTANCE = it }
            }
    }
}

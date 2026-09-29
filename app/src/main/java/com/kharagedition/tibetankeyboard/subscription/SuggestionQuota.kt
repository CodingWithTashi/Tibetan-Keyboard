package com.kharagedition.tibetankeyboard.subscription

/** What the suggestion strip does for this user right now. */
enum class SuggestionMode {
    /** Real, tappable suggestions. */
    LIVE,

    /** Suggestions shown locked; a tap opens the paywall. */
    LOCKED,
}

/** Free suggestions accepted on [day] (local epoch day). */
data class QuotaState(val day: Long, val used: Int)

/**
 * Free users get a daily taste of next-word suggestions — the main PRO feature, which they
 * otherwise never see. After `limit` accepted suggestions the strip shows them locked until
 * tomorrow. Pure so the counting rules are unit-testable; persisted by `SuggestionQuotaStore`.
 */
object SuggestionQuota {
    const val DEFAULT_DAILY_LIMIT = 20

    /**
     * Whether the daily limit applies at all: only to someone known to be free, and only where
     * PRO can be bought. Unknown PRO status (RevenueCat hasn't answered yet) stays unlimited so a
     * subscriber never sees locked chips; locking suggestions for someone who can't pay (Bhutan
     * before card checkout is live) would cost goodwill and earn nothing.
     */
    fun applies(isPremium: Boolean?, canSell: Boolean): Boolean = isPremium == false && canSell

    /** Today's state: a new day starts from zero (also if the clock went backwards). */
    fun rollover(state: QuotaState, today: Long): QuotaState =
        if (state.day == today) state else QuotaState(today, 0)

    fun remaining(state: QuotaState, today: Long, limit: Int): Int =
        (limit - rollover(state, today).used).coerceAtLeast(0)

    fun afterAccept(state: QuotaState, today: Long): QuotaState {
        val current = rollover(state, today)
        return current.copy(used = current.used + 1)
    }

    /** Whether accepting one more suggestion from [state] uses up today's last free one. */
    fun usesLastFree(state: QuotaState, today: Long, limit: Int): Boolean = remaining(state, today, limit) == 1

    fun mode(isPremium: Boolean?, canSell: Boolean, state: QuotaState, today: Long, limit: Int): SuggestionMode =
        if (!applies(isPremium, canSell) || remaining(state, today, limit) > 0) SuggestionMode.LIVE
        else SuggestionMode.LOCKED
}

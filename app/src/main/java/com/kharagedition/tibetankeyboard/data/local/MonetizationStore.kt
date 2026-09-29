package com.kharagedition.tibetankeyboard.data.local

import android.content.Context

/**
 * Small persisted state behind the upsell logic: when the onboarding paywall was shown, and the
 * last purchase failure that proves Google Play can't sell to this account (see
 * `BillingAvailability`). App-private; nothing here leaves the device.
 */
class MonetizationStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── onboarding paywall ───────────────────────────────────────────────────

    /** Local epoch day of the first automatic showing, or null if it never showed. */
    fun onboardingFirstShownDay(): Long? =
        prefs.getLong(KEY_ONBOARDING_FIRST_DAY, -1L).takeIf { it >= 0 }

    fun onboardingDay3Shown(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DAY3_SHOWN, false)

    /** Recorded as the paywall opens (not before), so a check that never shows it doesn't count. */
    fun markOnboardingShown(today: Long) {
        prefs.edit().putLong(KEY_ONBOARDING_FIRST_DAY, today).apply()
    }

    fun markOnboardingDay3Shown() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DAY3_SHOWN, true).apply()
    }

    /** Debug builds: forget that the onboarding paywall was shown, so it can be tested again. */
    fun debugResetOnboarding() {
        prefs.edit().remove(KEY_ONBOARDING_FIRST_DAY).remove(KEY_ONBOARDING_DAY3_SHOWN).apply()
    }

    // ── billing ──────────────────────────────────────────────────────────────

    fun lastHardFailureCode(): String? = prefs.getString(KEY_HARD_FAIL_CODE, null)

    fun lastHardFailureAtMs(): Long = prefs.getLong(KEY_HARD_FAIL_AT, 0L)

    fun recordHardFailure(code: String, atMs: Long = System.currentTimeMillis()) {
        prefs.edit().putString(KEY_HARD_FAIL_CODE, code).putLong(KEY_HARD_FAIL_AT, atMs).apply()
    }

    /**
     * Debug builds only: pretend the Play storefront is [code] (e.g. "BT", "IN", "NP") so every
     * billing branch can be checked on one device. Ignored in release.
     */
    fun debugStorefrontOverride(): String? = prefs.getString(KEY_DEBUG_STOREFRONT, null)

    fun setDebugStorefrontOverride(code: String?) {
        prefs.edit().putString(KEY_DEBUG_STOREFRONT, code).apply()
    }

    /** Debug builds only: treat this account as free even if it holds PRO. Ignored in release. */
    fun debugForceFree(): Boolean = prefs.getBoolean(KEY_DEBUG_FORCE_FREE, false)

    fun setDebugForceFree(force: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_FORCE_FREE, force).apply()
    }

    /** The half-price retention offer was bought; it is offered once. */
    fun retentionOfferAccepted(): Boolean = prefs.getBoolean(KEY_RETENTION_ACCEPTED, false)

    fun markRetentionOfferAccepted() {
        prefs.edit().putBoolean(KEY_RETENTION_ACCEPTED, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "monetization"
        private const val KEY_ONBOARDING_FIRST_DAY = "onboarding_first_day"
        private const val KEY_ONBOARDING_DAY3_SHOWN = "onboarding_day3_shown"
        private const val KEY_HARD_FAIL_CODE = "billing_hard_fail_code"
        private const val KEY_HARD_FAIL_AT = "billing_hard_fail_at"
        private const val KEY_DEBUG_STOREFRONT = "debug_storefront"
        private const val KEY_DEBUG_FORCE_FREE = "debug_force_free"
        private const val KEY_RETENTION_ACCEPTED = "retention_offer_accepted"

        @Volatile
        private var INSTANCE: MonetizationStore? = null

        fun getInstance(context: Context): MonetizationStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MonetizationStore(context).also { INSTANCE = it }
            }
    }
}

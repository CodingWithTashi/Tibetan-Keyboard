package com.kharagedition.tibetankeyboard.util

import android.content.Context
import android.content.Intent
import com.kharagedition.tibetankeyboard.BuildConfig
import com.kharagedition.tibetankeyboard.data.local.MonetizationStore
import com.kharagedition.tibetankeyboard.data.local.SuggestionQuotaStore
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager

private const val EXTRA_DEBUG_FORCE_FREE = "debug_force_free"
private const val EXTRA_DEBUG_STOREFRONT = "debug_storefront"
private const val EXTRA_DEBUG_SUGGESTIONS_USED = "debug_suggestions_used"
private const val EXTRA_DEBUG_RESET_ONBOARDING = "debug_reset_onboarding"

/**
 * Debug builds only, for checking the free-user and Bhutan paths on a device that holds PRO:
 * `adb shell am start -n com.kharagedition.tibetankeyboard/.ui.splash.SplashScreenActivity
 *    --ez debug_force_free true --es debug_storefront BT`
 * (`--es debug_storefront none` clears the storefront; `--ei debug_suggestions_used 20` uses up
 * today's free suggestions; `--ez debug_reset_onboarding true` lets the onboarding paywall show
 * again). Release builds ignore these extras.
 */
fun applyDebugMonetizationOverrides(context: Context, intent: Intent?) {
    if (!BuildConfig.DEBUG || intent == null) return
    val store = MonetizationStore.getInstance(context)
    if (intent.hasExtra(EXTRA_DEBUG_FORCE_FREE)) {
        store.setDebugForceFree(intent.getBooleanExtra(EXTRA_DEBUG_FORCE_FREE, false))
        RevenueCatManager.getInstance().refreshCustomerInfo()
    }
    if (intent.hasExtra(EXTRA_DEBUG_SUGGESTIONS_USED)) {
        SuggestionQuotaStore.getInstance(context).debugSetUsedToday(intent.getIntExtra(EXTRA_DEBUG_SUGGESTIONS_USED, 0))
    }
    if (intent.getBooleanExtra(EXTRA_DEBUG_RESET_ONBOARDING, false)) {
        store.debugResetOnboarding()
    }
    if (intent.hasExtra(EXTRA_DEBUG_STOREFRONT)) {
        // Only a two-letter country code sets it; anything else ("none") clears it.
        val code = intent.getStringExtra(EXTRA_DEBUG_STOREFRONT)?.trim()?.uppercase()
        store.setDebugStorefrontOverride(code?.takeIf { it.length == 2 && it.all(Char::isLetter) })
    }
}

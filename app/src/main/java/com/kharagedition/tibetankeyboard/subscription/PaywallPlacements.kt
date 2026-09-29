package com.kharagedition.tibetankeyboard.subscription

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.UpgradeSource

/**
 * Maps the lock a user tapped ([UpgradeSource]) to a RevenueCat placement id. Each placement can
 * get its own offering and paywall from the dashboard (Targeting), and RevenueCat attributes the
 * sale to it — so which gate sells is measurable without a release.
 *
 * The ids must match the placements created in the RevenueCat dashboard.
 */
object PaywallPlacements {
    const val ONBOARDING = "onboarding"
    const val ONBOARDING_DAY3 = "onboarding_day3"
    const val ONBOARDING_EXISTING = "onboarding_existing"
    const val HOME = "home"
    const val FEATURE_GATE = "feature_gate"
    const val KEYBOARD = "keyboard"
    const val KEYBOARD_SUGGESTIONS = "keyboard_suggestions"
    const val JOURNEY = "journey"
    const val SETTINGS = "settings"
    const val CHAT = "chat"
    const val TRANSLATE = "translate"
    const val DEFAULT = "default"

    fun forSource(source: String): String = when (source) {
        UpgradeSource.ONBOARDING -> ONBOARDING
        UpgradeSource.ONBOARDING_DAY3 -> ONBOARDING_DAY3
        UpgradeSource.ONBOARDING_EXISTING -> ONBOARDING_EXISTING
        UpgradeSource.HOME -> HOME
        UpgradeSource.HOME_FEATURE_GATE -> FEATURE_GATE
        UpgradeSource.KEYBOARD -> KEYBOARD
        UpgradeSource.KEYBOARD_SUGGESTIONS -> KEYBOARD_SUGGESTIONS
        UpgradeSource.JOURNEY -> JOURNEY
        UpgradeSource.SETTINGS -> SETTINGS
        UpgradeSource.CHAT -> CHAT
        UpgradeSource.TRANSLATE -> TRANSLATE
        else -> DEFAULT
    }
}

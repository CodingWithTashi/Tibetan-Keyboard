package com.kharagedition.tibetankeyboard.subscription

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteConfigTest {

    @Test
    fun emptyMetadata_givesDefaults() {
        assertEquals(RemoteConfig(), RemoteConfig.fromMetadata(emptyMap()))
    }

    @Test
    fun readsEachSwitch() {
        val c = RemoteConfig.fromMetadata(
            mapOf(
                RemoteConfig.META_FREE_SUGGESTIONS to 10,
                RemoteConfig.META_ONBOARDING_ENABLED to false,
                RemoteConfig.META_WEB_CHECKOUT_COUNTRIES to listOf("bt"),
            )
        )
        assertEquals(10, c.freeSuggestionsPerDay)
        assertEquals(false, c.onboardingPaywallEnabled)
        assertEquals(setOf("BT"), c.webCheckoutCountries)
    }

    @Test
    fun malformedValues_fallBack() {
        val c = RemoteConfig.fromMetadata(
            mapOf(
                RemoteConfig.META_FREE_SUGGESTIONS to -5,
                RemoteConfig.META_ONBOARDING_ENABLED to "yes",
                RemoteConfig.META_WEB_CHECKOUT_COUNTRIES to 42,
            )
        )
        assertEquals(RemoteConfig(), c)
    }

    @Test
    fun webCheckout_canBeTurnedOff_withAnEmptyList() {
        assertEquals(emptySet<String>(), RemoteConfig.fromMetadata(mapOf(RemoteConfig.META_WEB_CHECKOUT_COUNTRIES to "")).webCheckoutCountries)
        assertEquals(emptySet<String>(), RemoteConfig.fromMetadata(mapOf(RemoteConfig.META_WEB_CHECKOUT_COUNTRIES to emptyList<String>())).webCheckoutCountries)
    }

    @Test
    fun webCheckout_neverSpreadsToPlayBillingCountries() {
        // A typo or an added country must not enable card checkout where Play can sell (policy).
        val c = RemoteConfig.fromMetadata(mapOf(RemoteConfig.META_WEB_CHECKOUT_COUNTRIES to "BT, IN,NP"))
        assertEquals(setOf("BT"), c.webCheckoutCountries)
        assertEquals(emptySet<String>(), RemoteConfig.fromMetadata(mapOf(RemoteConfig.META_WEB_CHECKOUT_COUNTRIES to listOf("IN"))).webCheckoutCountries)
    }
}

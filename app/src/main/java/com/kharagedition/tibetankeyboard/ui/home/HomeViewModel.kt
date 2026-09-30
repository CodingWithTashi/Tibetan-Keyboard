package com.kharagedition.tibetankeyboard.ui.home

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.data.local.MonetizationStore
import com.kharagedition.tibetankeyboard.data.local.TypingStatsStore
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.util.localEpochDay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the Home UI state: keyboard-setup progress (fed by the Activity, which queries the
 * framework InputMethodManager) and premium status (observed from RevenueCat). Also exposes
 * the auth/session operations so the Activity stays free of business logic.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val authManager = AuthManager(app)
    private val premiumLiveData = RevenueCatManager.getInstance().isPremiumUser

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val premiumObserver = Observer<Boolean> { isPremium ->
        _uiState.update { it.copy(isPremium = isPremium) }
    }

    private val setupPrefs = app.getSharedPreferences(SETUP_PREFS, Application.MODE_PRIVATE)
    private val monetization = MonetizationStore.getInstance(app)

    /**
     * One-shot "open the onboarding paywall" requests. Conflated: if Home is in the background the
     * latest request waits instead of stacking up, and [isFresh] drops it if it waited too long.
     */
    private val _openPaywall = Channel<OnboardingPaywallRequest>(Channel.CONFLATED)
    val openPaywall: Flow<OnboardingPaywallRequest> = _openPaywall.receiveAsFlow()
    private var onboardingCheckInFlight = false

    init {
        premiumLiveData.observeForever(premiumObserver)
        RevenueCatManager.getInstance().refreshCustomerInfo()
        refreshAccount()
    }

    /** Re-read who is signed in (every resume: signing in and out happen on other screens). */
    fun refreshAccount() {
        val account = if (authManager.isUserAuthenticated()) {
            HomeAccount(
                name = authManager.getCurrentUserName(),
                photoUrl = authManager.getCurrentUserPhotoUrl().takeIf { it.isNotBlank() },
            )
        } else null
        _uiState.update { it.copy(account = account) }
    }

    /** Called by the Activity after it reads keyboard-enabled / default-IME status. */
    fun refreshSetup(keyboardEnabled: Boolean, inputMethodSelected: Boolean) {
        _uiState.update { it.copy(keyboardEnabled = keyboardEnabled, inputMethodSelected = inputMethodSelected) }
        val setupComplete = keyboardEnabled && inputMethodSelected
        rememberSetupStateBeforeOnboardingPaywall(setupComplete)
        if (setupComplete) {
            logSetupCompletedOnce()
            maybeOpenOnboardingPaywall()
        }
    }

    /**
     * People who set the keyboard up before this version get the onboarding paywall once as
     * [AppAnalytics.UpgradeSource.ONBOARDING_EXISTING]. Written once, on the first setup check of
     * this version: setup already complete at that point means it was done earlier. (The
     * setup-logged flag alone misses anyone who set up before it existed, in 2.2.12.)
     */
    private fun rememberSetupStateBeforeOnboardingPaywall(setupCompleteNow: Boolean) {
        if (setupPrefs.contains(KEY_SETUP_BEFORE_ONBOARDING)) return
        val alreadyDone = setupCompleteNow || setupPrefs.getBoolean(KEY_SETUP_LOGGED, false)
        setupPrefs.edit().putBoolean(KEY_SETUP_BEFORE_ONBOARDING, alreadyDone).apply()
    }

    /**
     * The soft upsell right after setup (and on day 3). A local pre-check with what is already
     * known runs first, so the common cases — already shown, PRO, switched off — cost no network
     * call on each resume.
     */
    private fun maybeOpenOnboardingPaywall() {
        if (onboardingCheckInFlight) return
        val revenueCat = RevenueCatManager.getInstance()
        val today = localEpochDay()
        val alreadyDone = setupPrefs.getBoolean(KEY_SETUP_BEFORE_ONBOARDING, false)
        fun decide(isPremium: Boolean?, enabled: Boolean) = OnboardingPaywallPolicy.decide(
            setupWasAlreadyDone = alreadyDone,
            isPremium = isPremium,
            enabled = enabled,
            firstShownDay = monetization.onboardingFirstShownDay(),
            day3Shown = monetization.onboardingDay3Shown(),
            today = today,
        )
        // A cached "PRO" is trustworthy; a cached "free" may just mean "not answered yet".
        val knownPremium = revenueCat.isPremiumKnown && revenueCat.isPremiumUserCached()
        if (decide(knownPremium, revenueCat.remoteConfig.onboardingPaywallEnabled) == OnboardingPaywall.NONE) return

        onboardingCheckInFlight = true
        viewModelScope.launch {
            try {
                // Past this, the "keyboard ready" moment is gone; the next resume tries again.
                val decision = withTimeoutOrNull(ONBOARDING_CHECK_TIMEOUT_MS) {
                    val isPremium = revenueCat.awaitIsPremium()
                    revenueCat.loadOfferings() // refreshes the dashboard kill switch
                    // Never pop a paywall at someone who can't pay (Bhutan before card checkout is live).
                    val canSell = revenueCat.canSellHere(revenueCat.storefrontCountry())
                    decide(isPremium, revenueCat.remoteConfig.onboardingPaywallEnabled && canSell)
                } ?: return@launch
                val source = when (decision) {
                    OnboardingPaywall.FIRST -> AppAnalytics.UpgradeSource.ONBOARDING
                    OnboardingPaywall.DAY3 -> AppAnalytics.UpgradeSource.ONBOARDING_DAY3
                    OnboardingPaywall.EXISTING -> AppAnalytics.UpgradeSource.ONBOARDING_EXISTING
                    OnboardingPaywall.NONE -> return@launch
                }
                // Let the "keyboard ready" moment land before the paywall slides in.
                delay(ONBOARDING_DELAY_MS)
                _openPaywall.send(OnboardingPaywallRequest(decision, source, today, SystemClock.elapsedRealtime()))
            } finally {
                onboardingCheckInFlight = false
            }
        }
    }

    /** False for a request that waited while Home was away; the next resume checks again. */
    fun isFresh(request: OnboardingPaywallRequest): Boolean =
        SystemClock.elapsedRealtime() - request.createdAtMs <= ONBOARDING_STALE_MS

    /**
     * The Activity is opening the paywall now. Only now is it recorded as shown, so a check that
     * never reached the screen (Home closed, request dropped) doesn't use up the showing.
     */
    fun onOnboardingPaywallOpened(request: OnboardingPaywallRequest) {
        when (request.decision) {
            OnboardingPaywall.DAY3 -> monetization.markOnboardingDay3Shown()
            else -> monetization.markOnboardingShown(request.day)
        }
        AppAnalytics.logOnboardingPaywallShown(request.source)
    }

    /** Once per install — `refreshSetup` runs on every resume, so the latch must be persisted. */
    private fun logSetupCompletedOnce() {
        if (setupPrefs.getBoolean(KEY_SETUP_LOGGED, false)) return
        setupPrefs.edit().putBoolean(KEY_SETUP_LOGGED, true).apply()
        AppAnalytics.logKeyboardSetupCompleted()
    }

    /** Re-read the on-device streak numbers for the Journey banner (cheap; every resume). */
    fun refreshJourney() {
        val stats = TypingStatsStore.getInstance(getApplication())
        _uiState.update { it.copy(streakDays = stats.displayStreak(), wordsToday = stats.wordsToday()) }
    }

    fun isUserAuthenticated(): Boolean = authManager.isUserAuthenticated()

    fun initializeUserSession(callback: RevenueCatManager.SubscriptionCallback) =
        authManager.initializeUserSession(callback)

    fun syncPurchases(callback: RevenueCatManager.SubscriptionCallback) =
        RevenueCatManager.getInstance().syncPurchases(callback)

    fun refreshPremium() = RevenueCatManager.getInstance().refreshCustomerInfo()

    override fun onCleared() {
        premiumLiveData.removeObserver(premiumObserver)
    }

    private companion object {
        const val SETUP_PREFS = "keyboard_setup"
        const val KEY_SETUP_LOGGED = "setup_completed_logged"
        const val KEY_SETUP_BEFORE_ONBOARDING = "setup_done_before_onboarding_paywall"
        const val ONBOARDING_DELAY_MS = 800L
        const val ONBOARDING_CHECK_TIMEOUT_MS = 5_000L
        const val ONBOARDING_STALE_MS = 10_000L
    }
}

/** Home should open the onboarding paywall: [decision] from [OnboardingPaywallPolicy], for [source]. */
data class OnboardingPaywallRequest(
    val decision: OnboardingPaywall,
    val source: String,
    val day: Long,
    val createdAtMs: Long,
)

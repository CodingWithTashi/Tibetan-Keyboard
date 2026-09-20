package com.kharagedition.tibetankeyboard.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.data.local.TypingStatsStore
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    init {
        premiumLiveData.observeForever(premiumObserver)
        RevenueCatManager.getInstance().refreshCustomerInfo()
    }

    /** Called by the Activity after it reads keyboard-enabled / default-IME status. */
    fun refreshSetup(keyboardEnabled: Boolean, inputMethodSelected: Boolean) {
        _uiState.update { it.copy(keyboardEnabled = keyboardEnabled, inputMethodSelected = inputMethodSelected) }
        if (keyboardEnabled && inputMethodSelected) logSetupCompletedOnce()
    }

    /** Once per install — `refreshSetup` runs on every resume, so the latch must be persisted. */
    private fun logSetupCompletedOnce() {
        val prefs = getApplication<Application>()
            .getSharedPreferences(SETUP_PREFS, Application.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SETUP_LOGGED, false)) return
        prefs.edit().putBoolean(KEY_SETUP_LOGGED, true).apply()
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
    }
}

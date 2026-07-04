package com.kharagedition.tibetankeyboard.ui.journey

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.data.local.TypingStatsStore
import com.kharagedition.tibetankeyboard.data.local.UserPreferences
import com.kharagedition.tibetankeyboard.data.repository.JourneyRepository
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable UI state for the Journey (streak & insights) screen. Numbers only, by design. */
data class JourneyUiState(
    val streakDays: Int = 0,
    val bestStreak: Int = 0,
    val typedToday: Boolean = false,
    val wordsToday: Int = 0,
    val wordsThisWeek: Int = 0,
    val charsThisWeek: Int = 0,
    val totalWords: Long = 0L,
    val vocabularySize: Int = 0,
    /** Words per day for the last 7 days, oldest first (today last). */
    val weekWords: List<Int> = List(7) { 0 },
    /** Single-letter weekday labels matching [weekWords]'s order. */
    val weekDayLabels: List<String> = WeekLabels.lastSevenDays(),
    val nextMilestone: Int? = StreakLogic.MILESTONES.first(),
    /** Set when the current streak sits exactly on a milestone — drives the celebration banner. */
    val milestone: Int? = null,
    val isPremium: Boolean = false,
    val isSignedIn: Boolean = false,
    val syncEnabled: Boolean = false,
    /** Community percentile (0–100) once the opt-in comparison has run; null otherwise. */
    val percentile: Int? = null,
    val comparing: Boolean = false,
    /** True when a signed-in sync attempt came back empty — distinct from "not signed in". */
    val syncFailed: Boolean = false,
)

/**
 * Owns the Journey UI state: everything is read from the on-device [TypingStatsStore];
 * the only network touch is the opt-in, numbers-only community comparison.
 */
class JourneyViewModel(app: Application) : AndroidViewModel(app) {

    private val stats = TypingStatsStore.getInstance(app)
    private val repository = JourneyRepository()
    private val authManager = AuthManager(app)
    private val premiumLiveData = RevenueCatManager.getInstance().isPremiumUser

    private val _uiState = MutableStateFlow(JourneyUiState())
    val uiState: StateFlow<JourneyUiState> = _uiState.asStateFlow()

    private val premiumObserver = Observer<Boolean> { isPremium ->
        _uiState.update { it.copy(isPremium = isPremium) }
    }

    init {
        premiumLiveData.observeForever(premiumObserver)
        RevenueCatManager.getInstance().refreshCustomerInfo()
        refresh()
    }

    /** Re-read the on-device stats (cheap; called on every resume). */
    fun refresh() {
        val today = stats.todayEpochDay()
        val streakDays = stats.displayStreak()
        _uiState.update {
            it.copy(
                streakDays = streakDays,
                bestStreak = stats.streak().bestDays,
                typedToday = stats.streak().lastActiveEpochDay == today,
                wordsToday = stats.wordsToday(),
                wordsThisWeek = stats.wordsThisWeek(today),
                charsThisWeek = stats.charsThisWeek(today),
                totalWords = stats.totalWords(),
                vocabularySize = stats.vocabularySize(),
                weekWords = stats.weekWords(today),
                weekDayLabels = WeekLabels.lastSevenDays(),
                nextMilestone = StreakLogic.nextMilestone(streakDays),
                milestone = streakDays.takeIf { it in StreakLogic.MILESTONES },
                syncEnabled = stats.isSyncEnabled(),
                isSignedIn = authManager.isUserAuthenticated(),
            )
        }
        if (stats.isSyncEnabled()) compare()
    }

    /**
     * Toggle the community comparison. Enabling immediately runs one numbers-only sync;
     * disabling stops all future syncs (nothing else is stored server-side by the client).
     */
    fun setSyncEnabled(enabled: Boolean) {
        stats.setSyncEnabled(enabled)
        AppAnalytics.logJourneySyncToggled(enabled)
        _uiState.update { it.copy(syncEnabled = enabled, percentile = null) }
        if (enabled) compare()
    }

    private fun compare() {
        val userId = UserPreferences(getApplication()).getUserId()
        if (userId.isBlank() || _uiState.value.comparing) return
        _uiState.update { it.copy(comparing = true, syncFailed = false) }
        viewModelScope.launch {
            val state = _uiState.value
            val result = repository.submitWeekly(userId, state.wordsThisWeek, state.streakDays)
            _uiState.update {
                it.copy(
                    comparing = false,
                    percentile = result?.percentile ?: it.percentile,
                    syncFailed = result == null,
                )
            }
        }
    }

    override fun onCleared() {
        premiumLiveData.removeObserver(premiumObserver)
    }
}

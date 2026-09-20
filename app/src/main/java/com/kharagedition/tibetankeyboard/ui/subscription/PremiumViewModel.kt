package com.kharagedition.tibetankeyboard.ui.subscription

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the paywall renders. */
data class PremiumUiState(
    val plans: List<RevenueCatManager.PremiumPlan> = emptyList(),
    val selectedPlanId: String? = null,
    val isPremium: Boolean = false,
    val isLoading: Boolean = true,
)

/** Lambdas the paywall can invoke; mirrors the `XxxActions` convention of the other screens. */
class PremiumActions(
    val onBack: () -> Unit = {},
    val onSelectPlan: (RevenueCatManager.PremiumPlan) -> Unit = {},
    val onPurchase: () -> Unit = {},
    val onRestore: () -> Unit = {},
)

/**
 * Paywall state. Observes plans rather than reading once, so an offering that finishes loading
 * after `onResume` still appears instead of leaving the screen on its placeholder.
 */
class PremiumViewModel : ViewModel() {

    private val revenueCat = RevenueCatManager.getInstance()

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    private val premiumObserver = Observer<Boolean> { isPremium ->
        _uiState.update { it.copy(isPremium = isPremium) }
    }

    private val plansObserver = Observer<List<RevenueCatManager.PremiumPlan>> { plans ->
        _uiState.update { state ->
            state.copy(
                plans = plans,
                // Keep the user's choice across refreshes, else fall back to the recommended plan.
                selectedPlanId = state.selectedPlanId?.takeIf { id -> plans.any { it.id == id } }
                    ?: plans.firstOrNull { it.isRecommended }?.id
                    ?: plans.firstOrNull()?.id,
                isLoading = plans.isEmpty(),
            )
        }
    }

    init {
        revenueCat.isPremiumUser.observeForever(premiumObserver)
        revenueCat.plans.observeForever(plansObserver)
        revenueCat.refreshCustomerInfo()
    }

    fun selectPlan(planId: String) {
        _uiState.update { it.copy(selectedPlanId = planId) }
    }

    fun selectedPlan(): RevenueCatManager.PremiumPlan? {
        val s = _uiState.value
        return s.plans.firstOrNull { it.id == s.selectedPlanId } ?: s.plans.firstOrNull()
    }

    override fun onCleared() {
        revenueCat.isPremiumUser.removeObserver(premiumObserver)
        revenueCat.plans.removeObserver(plansObserver)
        super.onCleared()
    }
}

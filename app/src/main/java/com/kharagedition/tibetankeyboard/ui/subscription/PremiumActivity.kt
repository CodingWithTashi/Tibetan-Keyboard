package com.kharagedition.tibetankeyboard.ui.subscription

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.data.repository.subscriptionCallback
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.ui.home.HomeActivity
import com.kharagedition.tibetankeyboard.util.showToast

/** Compose paywall. UI state in [PremiumViewModel]; purchase/restore call RevenueCat with this Activity. */
class PremiumActivity : AppCompatActivity() {

    private val viewModel: PremiumViewModel by viewModels()

    /** The PRO lock that opened this paywall; carried into every purchase event for attribution. */
    private val upgradeSource: String
        get() = intent?.getStringExtra(EXTRA_UPGRADE_SOURCE) ?: AppAnalytics.UpgradeSource.UNKNOWN

    /** Stops a purchase-driven close also counting as an abandon. */
    private var closedAfterPurchase = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TibetanKeyboardTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var viewLogged by rememberSaveable { mutableStateOf(false) }
                // Premium users have nothing to buy, so they bounce without ever counting as a
                // view — logging this in onCreate let them inflate the paywall's denominator.
                LaunchedEffect(state.isPremium) {
                    if (state.isPremium) {
                        closedAfterPurchase = true
                        closePaywall()
                    } else if (!viewLogged) {
                        viewLogged = true
                        AppAnalytics.logPaywallViewed(upgradeSource)
                    }
                }

                PremiumScreen(
                    state = state,
                    actions = PremiumActions(
                        onBack = { closePaywall() },
                        onSelectPlan = { plan ->
                            viewModel.selectPlan(plan.id)
                            AppAnalytics.logPaywallPlanSelected(upgradeSource, plan.analyticsPlan)
                        },
                        onPurchase = { purchasePremium() },
                        onRestore = { restorePurchases() },
                    ),
                )
            }
        }
    }

    /**
     * Close the paywall without ever leaving an empty back stack. If this is the task root
     * (e.g. reached right after a login that finished the launching screen), go to Home.
     */
    private fun closePaywall() {
        if (!closedAfterPurchase) AppAnalytics.logPaywallDismissed(upgradeSource)
        if (isTaskRoot) {
            startActivity(Intent(this, HomeActivity::class.java))
        }
        finish()
    }

    private fun purchasePremium() {
        val plan = viewModel.selectedPlan() ?: return
        AppAnalytics.logPurchaseStarted(upgradeSource, plan.analyticsPlan)
        RevenueCatManager.getInstance().purchasePremium(
            activity = this,
            plan = plan,
            callback = object : RevenueCatManager.SubscriptionCallback {
                override fun onSuccess(message: String) {
                    AppAnalytics.logPurchaseCompleted(
                        source = upgradeSource,
                        plan = plan.analyticsPlan,
                        price = plan.priceAmount,
                        currency = plan.currencyCode,
                    )
                    closedAfterPurchase = true
                    showToast(message)
                    closePaywall()
                }

                override fun onError(error: String) =
                    onError(RevenueCatManager.ERROR_UNKNOWN, error)

                override fun onError(code: String, message: String) {
                    AppAnalytics.logPurchaseFailed(upgradeSource, plan.analyticsPlan, code, message)
                    showToast(message)
                }

                // Dismissing Play's sheet fired nothing before, hiding the funnel's largest drop.
                override fun onUserCancelled() {
                    AppAnalytics.logPurchaseCancelled(upgradeSource, plan.analyticsPlan)
                }
            },
        )
    }

    private fun restorePurchases() {
        RevenueCatManager.getInstance().syncPurchases(object : RevenueCatManager.SubscriptionCallback {
            override fun onSuccess(message: String) {
                AppAnalytics.logPurchaseRestored()
                showToast(getString(R.string.purchases_restored))
            }

            override fun onError(error: String) = onError(RevenueCatManager.ERROR_UNKNOWN, error)

            override fun onError(code: String, message: String) {
                AppAnalytics.logRestoreFailed(code, message)
                showToast(message)
            }

            override fun onUserCancelled() {}
        })
    }

    companion object {
        /** One of [AppAnalytics.UpgradeSource]. Set by `AuthManager.openPremium(source)`. */
        const val EXTRA_UPGRADE_SOURCE = "upgrade_source"
    }
}

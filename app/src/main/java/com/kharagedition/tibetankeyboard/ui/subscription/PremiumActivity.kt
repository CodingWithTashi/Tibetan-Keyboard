package com.kharagedition.tibetankeyboard.ui.subscription

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.ui.home.HomeActivity
import com.kharagedition.tibetankeyboard.util.openInAppTab
import com.kharagedition.tibetankeyboard.util.showToast
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.Paywall
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.ui.revenuecatui.PaywallOptions
import kotlinx.coroutines.launch

/**
 * The paywall. Shows, for the lock that opened it, a RevenueCat dashboard paywall, our own Compose
 * paywall as a fallback, or card checkout where Google Play can't sell. State and the purchase
 * funnel live in [PremiumViewModel]; this Activity only launches Play, login and the checkout tab.
 */
open class PremiumActivity : AppCompatActivity() {

    private val viewModel: PremiumViewModel by viewModels()

    /** PRO became active here, so closing isn't an abandoned paywall. */
    private var closedAfterPurchase = false
    private var closing = false

    /** Card checkout was opened; check for the purchase when the user comes back. */
    private var webCheckoutOpened = false

    /** Opened from the keyboard: closing returns to the app being typed in, not to Home. */
    protected open val returnsHomeWhenRoot: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webCheckoutOpened = savedInstanceState?.getBoolean(STATE_WEB_CHECKOUT_OPENED) ?: false
        // System back goes through the same close as the ✕ (RevenueCat's paywall handles its own).
        onBackPressedDispatcher.addCallback(this) { closePaywall() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is PremiumEvent.Message -> showToast(getString(event.text))
                        is PremiumEvent.ProActive -> {
                            if (event.announce) showToast(getString(R.string.premium_activated))
                            closedAfterPurchase = true
                            closePaywall()
                        }
                    }
                }
            }
        }

        setContent {
            TibetanKeyboardTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                when (val content = state.content) {
                    PaywallContent.Loading -> PaywallLoadingScreen(onBack = ::closePaywall)
                    is PaywallContent.Dashboard -> DashboardPaywall(content.offering)
                    PaywallContent.WebCheckout -> WebCheckoutScreen(
                        state = state,
                        actions = WebCheckoutActions(
                            onBack = ::closePaywall,
                            onSelectPlan = viewModel::selectWebPlan,
                            onContinue = ::continueToWebCheckout,
                            onCheckAgain = viewModel::checkWebPurchase,
                        ),
                    )
                    PaywallContent.Custom, PaywallContent.Error -> PremiumScreen(
                        state = state,
                        actions = PremiumActions(
                            onBack = ::closePaywall,
                            onSelectPlan = viewModel::selectPlan,
                            onPurchase = ::purchasePremium,
                            onRestore = viewModel::restore,
                            onRetry = viewModel::load,
                        ),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSignIn()
        if (webCheckoutOpened) {
            webCheckoutOpened = false
            viewModel.checkWebPurchase()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_WEB_CHECKOUT_OPENED, webCheckoutOpened)
    }

    @Composable
    private fun DashboardPaywall(offering: Offering) {
        // PaywallOptions compares lambdas in equals(), so build it once per offering.
        val options = remember(offering) {
            PaywallOptions.Builder(dismissRequest = ::closePaywall)
                .setOffering(offering)
                .setListener(funnelListener)
                .build()
        }
        // V2 paywalls apply the system-bar insets themselves, so this stays full-bleed.
        Box(Modifier.fillMaxSize()) { Paywall(options) }
    }

    /** RevenueCat's paywall does the buying; forward its events to the ViewModel's funnel. */
    private val funnelListener = object : PaywallListener {
        override fun onPurchaseStarted(rcPackage: Package) = viewModel.onDashboardPurchaseStarted(rcPackage)

        override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) =
            viewModel.onDashboardPurchaseCompleted(customerInfo)

        override fun onPurchaseError(error: PurchasesError) =
            viewModel.onDashboardPurchaseError(error.code.name, error.message)

        override fun onPurchaseCancelled() = viewModel.onDashboardPurchaseCancelled()

        override fun onRestoreCompleted(customerInfo: CustomerInfo) {
            if (customerInfo.entitlements.active.isNotEmpty()) AppAnalytics.logPurchaseRestored()
        }

        override fun onRestoreError(error: PurchasesError) = AppAnalytics.logRestoreFailed(error.code.name, error.message)
    }

    /**
     * Close without ever leaving an empty back stack. Safe to call more than once: RevenueCat
     * dismisses after a purchase while the premium observer also closes.
     */
    private fun closePaywall() {
        if (closing || isFinishing) return
        closing = true
        if (!closedAfterPurchase) AppAnalytics.logPaywallDismissed(viewModel.source)
        if (isTaskRoot && returnsHomeWhenRoot) {
            startActivity(Intent(this, HomeActivity::class.java))
        }
        finish()
    }

    /** Our own paywall's buy button (it is only enabled once plans have loaded). */
    private fun purchasePremium() {
        if (viewModel.uiState.value.isPurchasing) return
        val plan = viewModel.uiState.value.selectedPlan ?: return
        viewModel.onPurchaseStarted(plan)
        RevenueCatManager.getInstance().purchasePremium(
            activity = this,
            plan = plan,
            callback = object : RevenueCatManager.SubscriptionCallback {
                override fun onSuccess(message: String) = viewModel.onPurchaseSucceeded(plan)

                override fun onError(error: String) = onError(RevenueCatManager.ERROR_UNKNOWN, error)

                override fun onError(code: String, message: String) {
                    viewModel.onPurchaseError(plan, code, message)
                    showToast(message)
                }

                // Dismissing Play's sheet is the funnel's largest drop; count it.
                override fun onUserCancelled() = viewModel.onPurchaseCancelled(plan)
            },
        )
    }

    /**
     * Card checkout (Bhutan only). The purchase must land on a real account, so a signed-out user
     * signs in first and comes straight back here; then RevenueCat's hosted checkout opens in a
     * browser tab on the picked plan, and [onResume] checks for the purchase when the tab closes.
     */
    private fun continueToWebCheckout() {
        if (webCheckoutOpened) return // double tap
        if (!viewModel.uiState.value.isSignedIn) {
            AuthManager(this).redirectToLogin(returnAfterLogin = true, finishCaller = false)
            return
        }
        val url = viewModel.webCheckoutUrl() ?: run {
            showToast(getString(R.string.web_checkout_unavailable))
            return
        }
        if (openInAppTab(url)) {
            webCheckoutOpened = true
            viewModel.onWebCheckoutOpened()
        } else {
            showToast(getString(R.string.web_checkout_unavailable))
        }
    }

    companion object {
        /** One of [AppAnalytics.UpgradeSource]. Set by `AuthManager.openPremium(source)`. */
        const val EXTRA_UPGRADE_SOURCE = "upgrade_source"

        private const val STATE_WEB_CHECKOUT_OPENED = "web_checkout_opened"
    }
}

/**
 * The paywall when opened from the keyboard: its own task, so closing it returns to the app the
 * user was typing in instead of opening Home.
 */
class KeyboardPremiumActivity : PremiumActivity() {
    override val returnsHomeWhenRoot: Boolean = false
}

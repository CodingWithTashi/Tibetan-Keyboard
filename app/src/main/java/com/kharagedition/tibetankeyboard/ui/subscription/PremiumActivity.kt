package com.kharagedition.tibetankeyboard.ui.subscription

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppAnalytics.logPaywallViewed()
        setContent {
            TibetanKeyboardTheme {
                val priceLabel by viewModel.priceLabel.collectAsStateWithLifecycle()
                val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
                // Premium users have nothing to buy — close the paywall.
                LaunchedEffect(isPremium) { if (isPremium) closePaywall() }

                PremiumScreen(
                    priceLabel = priceLabel,
                    onBack = { closePaywall() },
                    onPurchase = { purchasePremium() },
                    onRestore = { restorePurchases() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPrice()
    }

    /**
     * Close the paywall without ever leaving an empty back stack. If this is the task root
     * (e.g. reached right after a login that finished the launching screen), go to Home.
     */
    private fun closePaywall() {
        if (isTaskRoot) {
            startActivity(Intent(this, HomeActivity::class.java))
        }
        finish()
    }

    private fun purchasePremium() {
        AppAnalytics.logPurchaseStarted()
        RevenueCatManager.getInstance().purchasePremium(this, subscriptionCallback(
            onSuccess = { AppAnalytics.logPurchaseCompleted(); showToast(it); closePaywall() },
            onError = { AppAnalytics.logPurchaseFailed(it); showToast(it) },
        ))
    }

    private fun restorePurchases() {
        RevenueCatManager.getInstance().syncPurchases(subscriptionCallback(
            onSuccess = { AppAnalytics.logPurchaseRestored(); showToast(getString(R.string.purchases_restored)) },
            onError = { showToast(it) },
        ))
    }
}

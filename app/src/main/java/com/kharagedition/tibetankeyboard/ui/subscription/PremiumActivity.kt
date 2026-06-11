package com.kharagedition.tibetankeyboard.ui.subscription

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.data.repository.subscriptionCallback
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.util.showToast

/** Compose paywall. UI state in [PremiumViewModel]; purchase/restore call RevenueCat with this Activity. */
class PremiumActivity : AppCompatActivity() {

    private val viewModel: PremiumViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TibetanKeyboardTheme {
                val priceLabel by viewModel.priceLabel.collectAsStateWithLifecycle()
                val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
                LaunchedEffect(isPremium) { if (isPremium) finish() }

                PremiumScreen(
                    priceLabel = priceLabel,
                    onBack = { finish() },
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

    private fun purchasePremium() {
        RevenueCatManager.getInstance().purchasePremium(this, subscriptionCallback(
            onSuccess = { showToast(it); finish() },
            onError = { showToast(it) },
        ))
    }

    private fun restorePurchases() {
        RevenueCatManager.getInstance().syncPurchases(subscriptionCallback(
            onSuccess = { showToast(getString(R.string.purchases_restored)) },
            onError = { showToast(it) },
        ))
    }
}

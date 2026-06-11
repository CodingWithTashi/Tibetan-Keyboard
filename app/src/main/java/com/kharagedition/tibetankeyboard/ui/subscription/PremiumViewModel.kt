package com.kharagedition.tibetankeyboard.ui.subscription

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the paywall's display state (price + premium status). Purchasing stays in the
 *  Activity since RevenueCat requires an Activity reference. */
class PremiumViewModel(app: Application) : AndroidViewModel(app) {

    private val premiumLiveData = RevenueCatManager.getInstance().isPremiumUser

    private val _priceLabel = MutableStateFlow(
        RevenueCatManager.getInstance().getPremiumPackageInfo().second ?: DEFAULT_PRICE
    )
    val priceLabel: StateFlow<String> = _priceLabel.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val premiumObserver = Observer<Boolean> { _isPremium.value = it }

    init {
        premiumLiveData.observeForever(premiumObserver)
        RevenueCatManager.getInstance().refreshCustomerInfo()
    }

    fun refreshPrice() {
        _priceLabel.value = RevenueCatManager.getInstance().getPremiumPackageInfo().second ?: DEFAULT_PRICE
    }

    override fun onCleared() {
        premiumLiveData.removeObserver(premiumObserver)
    }

    companion object {
        private const val DEFAULT_PRICE = "Premium"
    }
}

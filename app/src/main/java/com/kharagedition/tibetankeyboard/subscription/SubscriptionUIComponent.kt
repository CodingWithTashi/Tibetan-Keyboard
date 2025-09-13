package com.kharagedition.tibetankeyboard.subscription

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.imageview.ShapeableImageView
import com.kharagedition.tibetankeyboard.util.showLongToast
import com.kharagedition.tibetankeyboard.util.showToast

/**
 * UI component to handle subscription-related UI operations
 * This can be used in any activity/fragment that needs subscription functionality
 */
interface IsPremiumListener {
    fun onPremiumStatusChanged(isPremium: Boolean)
}
class SubscriptionUIComponent(
    private val activity: AppCompatActivity,
    private val buyPremiumButton: ShapeableImageView,
    private val isPremiumListener: IsPremiumListener
) : RevenueCatManager.SubscriptionCallback {

    private val revenueCatManager = RevenueCatManager.getInstance()

    init {
        setupUI()
        observeSubscriptionStatus()
    }

    private fun setupUI() {
        buyPremiumButton.setOnClickListener {
            purchasePremium()
        }
    }

    private fun observeSubscriptionStatus() {
        // Observe premium status changes
        revenueCatManager.isPremiumUser.observe(activity as LifecycleOwner) { isPremium ->
            isPremiumListener.onPremiumStatusChanged(isPremium)
        }

        // Observe loading state
        revenueCatManager.isLoading.observe(activity as LifecycleOwner) { isLoading ->
            buyPremiumButton.isEnabled = !isLoading
        }

        // Observe errors
        revenueCatManager.error.observe(activity as LifecycleOwner) { error ->
            error?.let {
                activity.showToast(it)
                revenueCatManager.clearError()
            }
        }
    }


    /**
     * Initiate premium purchase
     */
    fun purchasePremium() {
        revenueCatManager.purchasePremium(activity, this)
    }

    /**
     * Refresh subscription status
     */
    fun refreshSubscriptionStatus() {
        revenueCatManager.refreshCustomerInfo(this)
    }

    /**
     * Get premium package info for display
     */
    fun getPremiumPackageInfo(): Pair<String?, String?> {
        return revenueCatManager.getPremiumPackageInfo()
    }

    /**
     * Check if user is premium (cached)
     */
    fun isPremiumUser(): Boolean {
        return revenueCatManager.isPremiumUserCached()
    }

    // RevenueCatManager.SubscriptionCallback implementations
    override fun onSuccess(message: String) {
        activity.showLongToast(message)
    }

    override fun onError(error: String) {
        activity.showToast(error)
    }

    override fun onUserCancelled() {
        activity.showToast("Purchase cancelled")
    }
}
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
class SubscriptionUIComponent(
    private val activity: AppCompatActivity,
    private val buyPremiumButton: ShapeableImageView
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
            updatePremiumUI(isPremium)
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
     * Update UI based on premium status
     */
    private fun updatePremiumUI(isPremium: Boolean) {
        buyPremiumButton.visibility = if (isPremium) View.GONE else View.VISIBLE

        // You can add more UI updates here for premium users
        // For example: unlock premium features, change UI colors, etc.
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
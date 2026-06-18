package com.kharagedition.tibetankeyboard.data.repository

import android.app.Activity
import android.content.Context
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages premium feature access and usage limits
 */
class PremiumFeatureManager(private val context: Context) {

    private val revenueCat = RevenueCatManager.getInstance()

    enum class PremiumFeature(val featureKey: String, val dailyLimit: Int) {
        GRAMMAR_CHECK("grammar_check", 10),
        TRANSLITERATION("transliteration", 20),
        CHAT_MESSAGE("chat_message", 100),
        DOCUMENT_UPLOAD("document_upload", 5),
        CHAT_EXPORT("chat_export", 5),
    }

    /**
     * Check if a feature is available for the current user
     */
    fun isPremiumFeatureAvailable(feature: PremiumFeature): Boolean {
        return revenueCat.isPremiumUser.value == true
    }

    /**
     * Check remaining usage for a feature (returns -1 if unlimited)
     */
    fun getFeatureRemaining(feature: PremiumFeature): Int {
        return if (isPremiumFeatureAvailable(feature)) {
            -1 // Unlimited for premium
        } else {
            getRemainingFreeUsage(feature)
        }
    }

    /**
     * Increment usage counter for a feature
     */
    fun incrementFeatureUsage(feature: PremiumFeature) {
        if (!isPremiumFeatureAvailable(feature)) {
            val sharedPref = context.getSharedPreferences("premium_usage", Context.MODE_PRIVATE)
            val today = getTodayDateString()
            val key = "${feature.featureKey}_$today"
            val current = sharedPref.getInt(key, 0)
            sharedPref.edit().putInt(key, current + 1).apply()
        }
    }

    /**
     * Check if user has exceeded daily limit for a feature
     */
    fun hasExceededLimit(feature: PremiumFeature): Boolean {
        if (isPremiumFeatureAvailable(feature)) return false

        val remaining = getRemainingFreeUsage(feature)
        return remaining <= 0
    }

    /**
     * Get remaining free usage
     */
    private fun getRemainingFreeUsage(feature: PremiumFeature): Int {
        val sharedPref = context.getSharedPreferences("premium_usage", Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val key = "${feature.featureKey}_$today"
        val used = sharedPref.getInt(key, 0)
        return maxOf(0, feature.dailyLimit - used)
    }

    /**
     * Show premium dialog if feature not available
     */
    fun showPremiumDialog(activity: Activity, feature: PremiumFeature, onPurchase: (() -> Unit)? = null) {
        if (isPremiumFeatureAvailable(feature)) {
            return
        }

        val remaining = getFeatureRemaining(feature)
        val message = if (remaining > 0) {
            "You have $remaining uses remaining today.\n\nUpgrade to Premium for unlimited access!"
        } else {
            "Daily limit exceeded.\n\nUpgrade to Premium for unlimited access!"
        }

        showPremiumUpsellDialog(activity, feature.featureKey, message, onPurchase)
    }

    private fun showPremiumUpsellDialog(
        activity: Activity,
        featureName: String,
        message: String,
        onPurchase: (() -> Unit)? = null
    ) {
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle("Premium Feature")
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Upgrade") { _, _ ->
                revenueCatManager.purchasePremium(activity, object : com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager.SubscriptionCallback {
                    override fun onSuccess(message: String) {
                        onPurchase?.invoke()
                    }

                    override fun onError(error: String) {
                        // Handle error silently
                    }

                    override fun onUserCancelled() {
                        // User cancelled the purchase
                    }
                })
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)

        builder.show()
    }

    /**
     * Get today's date as a string (API level 23 compatible)
     */
    private fun getTodayDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    companion object {
        private lateinit var instance: PremiumFeatureManager

        fun initialize(context: Context) {
            instance = PremiumFeatureManager(context)
        }

        fun getInstance(context: Context? = null): PremiumFeatureManager {
            if (!::instance.isInitialized && context != null) {
                initialize(context)
            }
            return instance
        }
    }

    private val revenueCatManager: RevenueCatManager
        get() = RevenueCatManager.getInstance()
}

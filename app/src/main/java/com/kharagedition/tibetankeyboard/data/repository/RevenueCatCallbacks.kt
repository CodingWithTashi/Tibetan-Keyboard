package com.kharagedition.tibetankeyboard.data.repository

/**
 * Builds a [RevenueCatManager.SubscriptionCallback] from optional lambdas, so call sites
 * don't repeat the three-method anonymous object every time.
 *
 * Usage: `RevenueCatManager.getInstance().syncPurchases(subscriptionCallback(onError = { ... }))`
 */
inline fun subscriptionCallback(
    crossinline onSuccess: (String) -> Unit = {},
    crossinline onError: (String) -> Unit = {},
    crossinline onCancelled: () -> Unit = {},
): RevenueCatManager.SubscriptionCallback = object : RevenueCatManager.SubscriptionCallback {
    override fun onSuccess(message: String) = onSuccess(message)
    override fun onError(error: String) = onError(error)
    override fun onUserCancelled() = onCancelled()
}

package com.kharagedition.tibetankeyboard.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kharagedition.tibetankeyboard.BuildConfig
import com.revenuecat.purchases.*
import com.revenuecat.purchases.interfaces.*
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.interfaces.SyncPurchasesCallback

/**
 * Singleton class to manage RevenueCat subscription logic
 * This handles all subscription-related operations in one place
 */
class RevenueCatManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: RevenueCatManager? = null
        private const val PREMIUM_ENTITLEMENT_ID = "pro"
        private const val TAG = "RevenueCatManager"

        fun getInstance(): RevenueCatManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RevenueCatManager().also { INSTANCE = it }
            }
        }
    }

    private var isConfigured = false

    private val _isPremiumUser = MutableLiveData<Boolean>()
    val isPremiumUser: LiveData<Boolean> = _isPremiumUser

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var currentOffering: Offering? = null
    private var premiumPackage: Package? = null

    interface SubscriptionCallback {
        fun onSuccess(message: String)
        fun onError(error: String)
        fun onUserCancelled()
    }

    /**
     * Initialize RevenueCat SDK with Firebase user ID
     * This must be called BEFORE any purchase operations
     */
    fun initialize(context: Context, firebaseAuth: FirebaseAuth, callback: SubscriptionCallback? = null) {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "Cannot initialize RevenueCat: User not authenticated")
            callback?.onError("User not authenticated")
            return
        }

        val userId = currentUser.uid
        Log.d(TAG, "Initializing RevenueCat with Firebase UID: $userId")

        _isLoading.value = true

        // Configure RevenueCat SDK with the Firebase user ID
        if (!isConfigured) {
            try {
                val apiKey = if (BuildConfig.DEBUG) {
                    "goog_HqifnUJxdgpKcyrUFhRfJfAYIap"
                } else {
                    "goog_HqifnUJxdgpKcyrUFhRfJfAYIap"
                }

                Purchases.configure(
                    PurchasesConfiguration.Builder(context, apiKey)
                        .appUserID(userId)  // CRITICAL: Set Firebase UID as app user ID
                        .purchasesAreCompletedBy(PurchasesAreCompletedBy.REVENUECAT)
                        .build()
                )

                // Setup customer info update listener
                Purchases.sharedInstance.updatedCustomerInfoListener =
                    UpdatedCustomerInfoListener { customerInfo ->
                        Log.d(TAG, "Customer Info Updated: ${customerInfo.originalAppUserId}")
                        updatePremiumStatus(customerInfo)
                    }

                isConfigured = true
                Log.d(TAG, "RevenueCat SDK configured successfully with user: $userId")

                // CRITICAL: Sync any pending purchases to acknowledge them with Google Play
                syncPurchasesWithGooglePlay()

                // Fetch customer info and offerings
                fetchCustomerInfoAndOfferings(callback)

            } catch (e: Exception) {
                _isLoading.value = false
                val errorMsg = "Failed to configure RevenueCat: ${e.message}"
                Log.e(TAG, errorMsg, e)
                _error.value = errorMsg
                callback?.onError(errorMsg)
            }
        } else {
            // Already configured this process. After a logout we call Purchases.logOut(), which
            // switches RevenueCat to a fresh ANONYMOUS user — so on re-login we must explicitly
            // re-identify the Firebase user with logIn() BEFORE reading entitlements. Without this,
            // a logout→login in the same session reads the anonymous user's (empty) entitlements and
            // a paying user is wrongly shown as not subscribed. logIn() also handles account
            // switching (user A → user B) and is a cheap no-op when the user is already current.
            Log.d(TAG, "RevenueCat already configured; identifying user before refresh: $userId")
            Purchases.sharedInstance.logIn(userId, object : LogInCallback {
                override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                    Log.d(TAG, "RevenueCat logIn success (created=$created) for $userId")
                    // Sync purchases to ensure Google Play acknowledgment, then refresh entitlements.
                    syncPurchasesWithGooglePlay()
                    fetchCustomerInfoAndOfferings(callback)
                }

                override fun onError(error: PurchasesError) {
                    _isLoading.value = false
                    val errorMsg = "RevenueCat logIn failed: ${error.message}"
                    Log.e(TAG, errorMsg)
                    _error.value = errorMsg
                    callback?.onError(errorMsg)
                }
            })
        }
    }

    /**
     * Sync purchases with Google Play to acknowledge them
     * This is CRITICAL to prevent Google from auto-cancelling subscriptions after 3 days
     */
    private fun syncPurchasesWithGooglePlay() {
        if (!isConfigured) {
            Log.w(TAG, "Cannot sync purchases: RevenueCat not configured")
            return
        }

        Log.d(TAG, "Syncing purchases with Google Play to acknowledge subscriptions...")
        Purchases.sharedInstance.syncPurchases(object : SyncPurchasesCallback {
            override fun onSuccess(customerInfo: CustomerInfo) {
                Log.d(TAG, "✅ Purchases synced successfully with Google Play")
                Log.d(TAG, "Active subscriptions after sync: ${customerInfo.activeSubscriptions.joinToString()}")
                updatePremiumStatus(customerInfo)
            }

            override fun onError(error: PurchasesError) {
                Log.e(TAG, "❌ Failed to sync purchases: ${error.message}")
                Log.e(TAG, "Error code: ${error.code}")
            }
        })
    }

    /**
     * Fetch customer info and offerings after initialization
     */
    private fun fetchCustomerInfoAndOfferings(callback: SubscriptionCallback?) {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                Log.d(TAG, "Customer info received - App User ID: ${customerInfo.originalAppUserId}")
                Log.d(TAG, "Active entitlements: ${customerInfo.activeSubscriptions}")
                updatePremiumStatus(customerInfo)
                fetchOfferings(callback)
            }

            override fun onError(error: PurchasesError) {
                _isLoading.value = false
                val errorMsg = "Failed to fetch customer info: ${error.message}"
                Log.e(TAG, errorMsg)
                _error.value = errorMsg
                callback?.onError(errorMsg)
            }
        })
    }

    /**
     * Fetch available offerings
     */
    private fun fetchOfferings(callback: SubscriptionCallback? = null) {
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) {
                _isLoading.value = false
                currentOffering = offerings.current

                if (currentOffering == null) {
                    val errorMsg = "Premium subscription not available"
                    _error.value = errorMsg
                    callback?.onError(errorMsg)
                    return
                }

                // Look for monthly premium package
                premiumPackage = currentOffering?.monthly ?: currentOffering?.availablePackages?.firstOrNull()

                if (premiumPackage != null) {
                    println("RevenueCat: Premium package loaded - ${premiumPackage?.product?.title}")
                    callback?.onSuccess("Premium services initialized")
                } else {
                    val errorMsg = "Premium subscription not available"
                    _error.value = errorMsg
                    callback?.onError(errorMsg)
                }
            }

            override fun onError(error: PurchasesError) {
                _isLoading.value = false
                val errorMsg = "Failed to load premium options: ${error.message}"
                _error.value = errorMsg
                callback?.onError(errorMsg)
            }
        })
    }

    /**
     * Check current customer info and update premium status
     * Note: RevenueCat must be initialized first
     */
    fun refreshCustomerInfo(callback: SubscriptionCallback? = null) {
        if (!isConfigured) {
            Log.w(TAG, "Cannot refresh customer info: RevenueCat not initialized")
            _isPremiumUser.value = false
            return
        }

        _isLoading.value = true

        // CRITICAL: First sync purchases to acknowledge any pending subscriptions
        syncPurchasesWithGooglePlay()

        Purchases.sharedInstance.getCustomerInfo(
            object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    _isLoading.value = false
                    Log.d(TAG, "Customer info refreshed - User ID: ${customerInfo.originalAppUserId}")
                    updatePremiumStatus(customerInfo)
                    callback?.onSuccess("Premium status updated")
                }

                override fun onError(error: PurchasesError) {
                    _isLoading.value = false
                    val errorMsg = "Failed to load premium status: ${error.message}"
                    Log.e(TAG, errorMsg)
                    _error.value = errorMsg
                    callback?.onError(errorMsg)
                }
            }
        )
    }

    /**
     * Force sync purchases with Google Play
     * Call this when app resumes or after a purchase to ensure acknowledgment
     */
    fun syncPurchases(callback: SubscriptionCallback? = null) {
        if (!isConfigured) {
            Log.w(TAG, "Cannot sync purchases: RevenueCat not initialized")
            callback?.onError("RevenueCat not initialized")
            return
        }

        Log.d(TAG, "🔄 Manually syncing purchases with Google Play...")
        Purchases.sharedInstance.syncPurchases(object : SyncPurchasesCallback {
            override fun onSuccess(customerInfo: CustomerInfo) {
                Log.d(TAG, "✅ Manual sync successful!")
                updatePremiumStatus(customerInfo)
                callback?.onSuccess("Purchases synced successfully")
            }

            override fun onError(error: PurchasesError) {
                Log.e(TAG, "❌ Manual sync failed: ${error.message}")
                callback?.onError("Sync failed: ${error.message}")
            }
        })
    }

    /**
     * Purchase premium subscription
     */
    fun purchasePremium(activity: Activity, callback: SubscriptionCallback) {
        if (!isConfigured) {
            val errorMsg = "RevenueCat not initialized. Please login first."
            Log.e(TAG, errorMsg)
            callback.onError(errorMsg)
            return
        }

        val packageToPurchase = premiumPackage
        if (packageToPurchase == null) {
            Log.e(TAG, "Premium package not available")
            callback.onError("Premium subscription not available")
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        Log.d(TAG, "==== Starting Purchase ====")
        Log.d(TAG, "Firebase User ID: $userId")
        Log.d(TAG, "Package: ${packageToPurchase.identifier}")
        Log.d(TAG, "Product: ${packageToPurchase.product.id}")
        Log.d(TAG, "========================")

        _isLoading.value = true
        val purchaseParams = PurchaseParams.Builder(activity, packageToPurchase).build()

        Purchases.sharedInstance.purchase(
            purchaseParams,
            object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    _isLoading.value = false
                    Log.d(TAG, "==== Purchase Successful ====")
                    Log.d(TAG, "Transaction ID: ${storeTransaction.orderId}")
                    Log.d(TAG, "Product IDs: ${storeTransaction.productIds.joinToString()}")
                    Log.d(TAG, "Customer ID: ${customerInfo.originalAppUserId}")
                    Log.d(TAG, "===========================")

                    // CRITICAL: Immediately sync purchases to acknowledge with Google Play
                    // This prevents the 3-day auto-cancellation issue
                    Log.d(TAG, "Syncing purchase immediately to acknowledge with Google Play...")
                    syncPurchasesWithGooglePlay()

                    updatePremiumStatus(customerInfo)
                    callback.onSuccess("Premium subscription activated!")
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    _isLoading.value = false
                    Log.e(TAG, "==== Purchase Error ====")
                    Log.e(TAG, "User Cancelled: $userCancelled")
                    Log.e(TAG, "Error Code: ${error.code}")
                    Log.e(TAG, "Error Message: ${error.message}")
                    Log.e(TAG, "=======================")

                    when {
                        userCancelled -> {
                            callback.onUserCancelled()
                        }
                        error.code == PurchasesErrorCode.ProductAlreadyPurchasedError -> {
                            Log.d(TAG, "Product already purchased, refreshing customer info")
                            callback.onError("You already own this subscription")
                            // Refresh customer info to update UI
                            refreshCustomerInfo()
                        }
                        else -> {
                            callback.onError("Purchase failed: ${error.message}")
                        }
                    }
                }
            }
        )
    }

    /**
     * Logout from RevenueCat
     */
    fun logout(callback: SubscriptionCallback? = null) {
        Purchases.sharedInstance.logOut(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                _isPremiumUser.value = false
                callback?.onSuccess("Logged out successfully")
            }

            override fun onError(error: PurchasesError) {
                // Logout failed, but we can still proceed
                _isPremiumUser.value = false
                callback?.onError("Logout error: ${error.message}")
            }
        })
    }

    /**
     * Update premium status based on customer info
     */
    private fun updatePremiumStatus(customerInfo: CustomerInfo) {
        val proEntitlement = customerInfo.entitlements[PREMIUM_ENTITLEMENT_ID]
        val isPremium = proEntitlement?.isActive == true

        // CRITICAL FIX: Do not use debug mode bypass in production
        _isPremiumUser.value = isPremium

        // Get subscription expiry date from entitlement
        val expiryDate = proEntitlement?.expirationDate
        val willRenew = proEntitlement?.willRenew ?: false
        val periodType = proEntitlement?.periodType?.toString() ?: "unknown"

        // Enhanced logging for debugging
        Log.d(TAG, "==== Premium Status Update ====")
        Log.d(TAG, "App User ID: ${customerInfo.originalAppUserId}")
        Log.d(TAG, "Premium Status: $isPremium")
        Log.d(TAG, "Expiry Date: $expiryDate")
        Log.d(TAG, "Will Renew: $willRenew")
        Log.d(TAG, "Period Type: $periodType")
        Log.d(TAG, "Active Subscriptions: ${customerInfo.activeSubscriptions.joinToString()}")
        Log.d(TAG, "All Entitlements: ${customerInfo.entitlements.all.keys.joinToString()}")
        Log.d(TAG, "Pro Entitlement Active: ${proEntitlement?.isActive}")
        Log.d(TAG, "Request Date: ${customerInfo.requestDate}")
        Log.d(TAG, "============================")

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val db = FirebaseFirestore.getInstance()
        if(userId==null) {
            Log.w(TAG, "Cannot update Firestore: User ID is null")
            return
        }

        if (isPremium) {
            // CRITICAL FIX: Use actual expiration date from entitlement, not request date
            val premiumDetails = hashMapOf(
                "isPremium" to true,
                "subscribed" to true,
                "isSubscribed" to true,
                "subscriptionType" to "premium",
                "activeSubscriptions" to customerInfo.activeSubscriptions.toList(),
                "premiumExpiryDate" to (expiryDate ?: customerInfo.requestDate), // Use actual expiry or fallback to request date
                "willRenew" to willRenew,
                "periodType" to periodType,
                "revenueCatUserId" to customerInfo.originalAppUserId,
                "lastUpdated" to customerInfo.requestDate,
                "originalPurchaseDate" to proEntitlement.originalPurchaseDate
            )
            val userRef = db.collection("users").document(userId)

            userRef.update(premiumDetails)
                .addOnSuccessListener {
                    Log.d(TAG, "Firestore: User premium details updated successfully for user: $userId")
                    Log.d(TAG, "Firestore: Subscription expires at: $expiryDate")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore: Failed to update premium details - ${e.message}", e)
                }
        } else {
            val userRef = db.collection("users").document(userId)

            userRef.update(mapOf<String, Any>(
                "isPremium" to false,
                "subscribed" to false,
                "isSubscribed" to false,
                "lastUpdated" to customerInfo.requestDate
            ))
                .addOnSuccessListener {
                    Log.d(TAG, "Firestore: User premium status set to false for user: $userId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore: Failed to set premium status - ${e.message}", e)
                }
        }
    }

    /**
     * Get premium package details for UI display
     */
    fun getPremiumPackageInfo(): Pair<String?, String?> {
        return Pair(
            premiumPackage?.product?.title,
            premiumPackage?.product?.price?.formatted
        )
    }

    /**
     * Check if user is premium without triggering network call
     */
    fun isPremiumUserCached(): Boolean {
        return _isPremiumUser.value ?: false
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }
}
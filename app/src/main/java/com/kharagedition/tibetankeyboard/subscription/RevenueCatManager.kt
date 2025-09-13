package com.kharagedition.tibetankeyboard.subscription

import android.app.Activity
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.revenuecat.purchases.*
import com.revenuecat.purchases.interfaces.*
import com.revenuecat.purchases.models.StoreTransaction

/**
 * Singleton class to manage RevenueCat subscription logic
 * This handles all subscription-related operations in one place
 */
class RevenueCatManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: RevenueCatManager? = null
        private const val PREMIUM_ENTITLEMENT_ID = "pro"

        fun getInstance(): RevenueCatManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RevenueCatManager().also { INSTANCE = it }
            }
        }
    }

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
     * Initialize RevenueCat with Firebase user
     */
    fun initialize(firebaseAuth: FirebaseAuth, callback: SubscriptionCallback? = null) {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            callback?.onError("User not authenticated")
            return
        }

        _isLoading.value = true

        Purchases.sharedInstance.logIn(
            currentUser.uid,
            object : LogInCallback {
                override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                    println("RevenueCat: User logged in successfully. Created: $created")
                    updatePremiumStatus(customerInfo)
                    fetchOfferings(callback)
                }

                override fun onError(error: PurchasesError) {
                    _isLoading.value = false
                    val errorMsg = "Failed to initialize premium services: ${error.message}"
                    _error.value = errorMsg
                    callback?.onError(errorMsg)
                }
            }
        )
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
     */
    fun refreshCustomerInfo(callback: SubscriptionCallback? = null) {
        _isLoading.value = true

        Purchases.sharedInstance.getCustomerInfo(
            object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    _isLoading.value = false
                    updatePremiumStatus(customerInfo)
                    callback?.onSuccess("Premium status updated")
                }

                override fun onError(error: PurchasesError) {
                    _isLoading.value = false
                    val errorMsg = "Failed to load premium status: ${error.message}"
                    _error.value = errorMsg
                    callback?.onError(errorMsg)
                }
            }
        )
    }

    /**
     * Purchase premium subscription
     */
    fun purchasePremium(activity: Activity, callback: SubscriptionCallback) {
        val packageToPurchase = premiumPackage
        if (packageToPurchase == null) {
            callback.onError("Premium subscription not available")
            return
        }

        _isLoading.value = true
        val purchaseParams = PurchaseParams.Builder(activity, packageToPurchase).build()

        Purchases.sharedInstance.purchase(
            purchaseParams,
            object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    _isLoading.value = false
                    updatePremiumStatus(customerInfo)
                    callback.onSuccess("Premium subscription activated!")
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    _isLoading.value = false

                    when {
                        userCancelled -> {
                            callback.onUserCancelled()
                        }
                        error.code == PurchasesErrorCode.ProductAlreadyPurchasedError -> {
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
        val isPremium = customerInfo.entitlements[PREMIUM_ENTITLEMENT_ID]?.isActive == true
        _isPremiumUser.value = isPremium

        println("RevenueCat: Premium status - $isPremium")

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val db = FirebaseFirestore.getInstance()
        if(userId==null) return

        if (isPremium) {
            val premiumDetails = hashMapOf(
                "isPremium" to true,
                "subscribed" to true,
                "isSubscribed" to true,
                "subscriptionType" to "premium",
                "activeSubscription" to customerInfo.activeSubscriptions.toList(),
                "premiumExpiryDate" to customerInfo.requestDate,
                "activeSubscriptions" to customerInfo.activeSubscriptions.toList()
            )
            val userRef = db.collection("users").document(userId)


            userRef.update(premiumDetails)
                .addOnSuccessListener {
                    println("Firestore: User premium details updated successfully.")
                }
                .addOnFailureListener { e ->
                    println("Firestore: Failed to update premium details - ${e.message}")
                }

            println("RevenueCat: Active subscriptions count - ${customerInfo.activeSubscriptions.size}")
        } else {
            val userRef = db.collection("users").document(userId)

            userRef.update(mapOf<String, Boolean>(
                "isPremium" to false,
                "subscribed" to false,
                "isSubscribed" to false,
            ))
                .addOnSuccessListener {
                    println("Firestore: User premium status set to false.")
                }
                .addOnFailureListener { e ->
                    println("Firestore: Failed to set premium status - ${e.message}")
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
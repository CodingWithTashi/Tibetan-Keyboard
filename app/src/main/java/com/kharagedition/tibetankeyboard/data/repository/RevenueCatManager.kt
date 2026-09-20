package com.kharagedition.tibetankeyboard.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.kharagedition.tibetankeyboard.BuildConfig
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
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

        /** Read by identifier, not `offerings.current`, so shipped clients keep the `sale` offering. */
        private const val OFFERING_ID = "pro_v2"

        // Stable codes for failures RevenueCat does not raise as a PurchasesErrorCode.
        const val ERROR_NOT_CONFIGURED = "SDK_NOT_CONFIGURED"
        const val ERROR_NO_OFFERING = "OFFERING_UNAVAILABLE"
        const val ERROR_NO_PACKAGE = "PACKAGE_UNAVAILABLE"
        const val ERROR_UNKNOWN = "UNKNOWN"

        fun getInstance(): RevenueCatManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RevenueCatManager().also { INSTANCE = it }
            }
        }
    }

    /** Mirrors [Purchases.isConfigured]. */
    private var isConfigured = false
        get() = field || Purchases.isConfigured

    // Seeded false so observers never see null before the first callback.
    private val _isPremiumUser = MutableLiveData<Boolean>(false)
    val isPremiumUser: LiveData<Boolean> = _isPremiumUser

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var currentOffering: Offering? = null

    /** Every plan the paywall can offer, ordered annual → monthly → lifetime. */
    private val _plans = MutableLiveData<List<PremiumPlan>>(emptyList())
    val plans: LiveData<List<PremiumPlan>> = _plans

    /** The plan the paywall pre-selects. Annual when present — it is the one we want bought. */
    val defaultPlan: PremiumPlan? get() = _plans.value?.firstOrNull { it.isRecommended } ?: _plans.value?.firstOrNull()

    /** One purchasable plan, flattened out of a [Package] so the UI never touches SDK types. */
    data class PremiumPlan(
        val id: String,
        val analyticsPlan: String,
        val title: String,
        val price: String,
        val pricePerMonth: String?,
        val savingPercent: Int?,
        val isRecommended: Boolean,
        val priceAmount: Double?,
        val currencyCode: String?,
        internal val rcPackage: Package,
    )

    interface SubscriptionCallback {
        fun onSuccess(message: String)
        fun onError(error: String)
        fun onUserCancelled()

        /** [onError] with a stable error code; the localized message alone is useless as a GA4 dimension. */
        fun onError(code: String, message: String) = onError(message)
    }

    /**
     * Configure for a signed-OUT user so the paywall works before anyone logs in; the anonymous
     * customer is merged into the Firebase UID by [initialize]'s `logIn()` on the next sign-in.
     */
    fun configureAnonymous(context: Context) {
        if (isConfigured) return
        try {
            Purchases.configure(
                PurchasesConfiguration.Builder(context, apiKey())
                    .purchasesAreCompletedBy(PurchasesAreCompletedBy.REVENUECAT)
                    .build()
            )
            attachCustomerInfoListener()
            isConfigured = true
            Log.i(TAG, "subs-flow: RevenueCat configured anonymously (no Firebase user yet)")
            fetchCustomerInfoAndOfferings(null)
        } catch (e: Exception) {
            Log.e(TAG, "subs-flow: anonymous configure failed: ${e.message}", e)
        }
    }

    private fun apiKey(): String = BuildConfig.REVENUECAT_API_KEY

    private fun attachCustomerInfoListener() {
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { customerInfo ->
                Log.d(TAG, "Customer Info Updated: ${customerInfo.originalAppUserId}")
                updatePremiumStatus(customerInfo)
            }
    }

    /** Identify the signed-in user; with no Firebase user this falls through to [configureAnonymous]. */
    fun initialize(context: Context, firebaseAuth: FirebaseAuth, callback: SubscriptionCallback? = null) {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "subs-flow: no Firebase user — configuring RevenueCat anonymously")
            configureAnonymous(context)
            callback?.onSuccess("Premium services initialized")
            return
        }

        val userId = currentUser.uid
        Log.d(TAG, "Initializing RevenueCat with Firebase UID: $userId")

        _isLoading.value = true

        // Configure RevenueCat SDK with the Firebase user ID
        if (!isConfigured) {
            try {
                val apiKey = apiKey()

                Purchases.configure(
                    PurchasesConfiguration.Builder(context, apiKey)
                        .appUserID(userId)  // CRITICAL: Set Firebase UID as app user ID
                        .purchasesAreCompletedBy(PurchasesAreCompletedBy.REVENUECAT)
                        .build()
                )

                // Setup customer info update listener
                attachCustomerInfoListener()

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
                // Fall back to `current` so a build shipped before pro_v2 is promoted still sells.
                val offering = offerings.getOffering(OFFERING_ID) ?: offerings.current
                currentOffering = offering

                if (offering == null) {
                    _error.value = "Premium subscription not available"
                    callback?.onError(ERROR_NO_OFFERING, "Premium subscription not available")
                    return
                }

                val built = buildPlans(offering)
                _plans.value = built

                if (built.isNotEmpty()) {
                    Log.i(TAG, "subs-flow: ${built.size} plan(s) from '${offering.identifier}': " +
                        built.joinToString { "${it.analyticsPlan}=${it.price}" })
                    callback?.onSuccess("Premium services initialized")
                } else {
                    _error.value = "Premium subscription not available"
                    callback?.onError(ERROR_NO_PACKAGE, "Premium subscription not available")
                }
            }

            override fun onError(error: PurchasesError) {
                _isLoading.value = false
                val errorMsg = "Failed to load premium options: ${error.message}"
                _error.value = errorMsg
                callback?.onError(error.code.name, errorMsg)
            }
        })
    }

    /** Flatten an [Offering] into plans. The saving uses real per-month prices — Play's regional
     *  tiers are not a constant multiple. */
    private fun buildPlans(offering: Offering): List<PremiumPlan> {
        val annual = offering.annual
        val monthly = offering.monthly
        val lifetime = offering.lifetime

        val monthlyMicros = monthly?.product?.price?.amountMicros
        val annualPerMonthMicros = annual?.product?.pricePerMonth()?.amountMicros

        val saving = if (monthlyMicros != null && annualPerMonthMicros != null && monthlyMicros > 0) {
            (100 - (annualPerMonthMicros * 100 / monthlyMicros)).toInt().takeIf { it > 0 }
        } else null

        fun plan(pkg: Package?, analytics: String, recommended: Boolean): PremiumPlan? {
            val p = pkg ?: return null
            val isLifetime = analytics == AppAnalytics.Plan.LIFETIME
            return PremiumPlan(
                id = p.identifier,
                analyticsPlan = analytics,
                title = p.product.title,
                price = p.product.price.formatted,
                pricePerMonth = if (isLifetime) null else p.product.formattedPricePerMonth(),
                savingPercent = if (analytics == AppAnalytics.Plan.ANNUAL) saving else null,
                isRecommended = recommended,
                priceAmount = p.product.price.amountMicros / 1_000_000.0,
                currencyCode = p.product.price.currencyCode,
                rcPackage = p,
            )
        }

        val plans = listOfNotNull(
            plan(annual, AppAnalytics.Plan.ANNUAL, recommended = true),
            plan(monthly, AppAnalytics.Plan.MONTHLY, recommended = false),
            plan(lifetime, AppAnalytics.Plan.LIFETIME, recommended = false),
        )

        // The live `sale` offering types its monthly product as `$rc_weekly`, so no typed
        // accessor matches it — fall back to whatever is there rather than show nothing.
        if (plans.isEmpty()) {
            return offering.availablePackages.map {
                PremiumPlan(
                    id = it.identifier,
                    analyticsPlan = AppAnalytics.Plan.UNKNOWN,
                    title = it.product.title,
                    price = it.product.price.formatted,
                    pricePerMonth = null,
                    savingPercent = null,
                    isRecommended = true,
                    priceAmount = it.product.price.amountMicros / 1_000_000.0,
                    currencyCode = it.product.price.currencyCode,
                    rcPackage = it,
                )
            }
        }
        return plans
    }

    /**
     * Check current customer info and update premium status
     * Note: RevenueCat must be initialized first
     */
    fun refreshCustomerInfo(callback: SubscriptionCallback? = null) {
        if (!isConfigured) {
            // Must fire the callback; returning silently left callers waiting forever.
            Log.w(TAG, "Cannot refresh customer info: RevenueCat not initialized")
            _isPremiumUser.value = false
            callback?.onError(ERROR_NOT_CONFIGURED, "Premium is still starting up")
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
            callback?.onError(ERROR_NOT_CONFIGURED, "Premium is still starting up")
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

    /** Buy [plan], or the recommended one. No Firebase user required — see [configureAnonymous]. */
    @JvmOverloads
    fun purchasePremium(
        activity: Activity,
        plan: PremiumPlan? = null,
        callback: SubscriptionCallback,
    ) {
        if (!isConfigured) {
            // Configured at app start, so this is a genuine failure — never ask the user to log in.
            val errorMsg = "Premium is still starting up. Please try again in a moment."
            Log.e(TAG, "subs-flow: purchase blocked — SDK not configured")
            callback.onError(ERROR_NOT_CONFIGURED, errorMsg)
            return
        }

        val packageToPurchase = (plan ?: defaultPlan)?.rcPackage
        if (packageToPurchase == null) {
            Log.e(TAG, "Premium package not available")
            callback.onError(ERROR_NO_PACKAGE, "Premium subscription not available")
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
                    Log.i(
                        TAG,
                        "subs-flow: purchase completed — appUserId=${currentAppUserId(customerInfo)} " +
                            "order=${storeTransaction.orderId} products=${storeTransaction.productIds.joinToString()}"
                    )
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
                            callback.onError(error.code.name, "You already own this subscription")
                            // Refresh customer info to update UI
                            refreshCustomerInfo()
                        }
                        else -> {
                            callback.onError(error.code.name, "Purchase failed: ${error.message}")
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
        // `sharedInstance` throws when never configured, and AuthManager.signOut() calls in here.
        if (!isConfigured) {
            _isPremiumUser.value = false
            callback?.onSuccess("Logged out successfully")
            return
        }
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
     * Update premium status based on customer info.
     *
     * This ONLY updates the in-app LiveData that drives the UI (locks/PRO tags),
     * which reflects instantly on purchase for good UX. It does NOT write to
     * Firestore: the backend is the single, authoritative writer of server-side
     * pro state (`users/{uid}.isPro`), kept in sync by the RevenueCat webhook +
     * a live REST fallback. Keeping the client out of Firestore avoids competing
     * writers / field sprawl and means pro gating can't be spoofed from the app.
     */
    private fun updatePremiumStatus(customerInfo: CustomerInfo) {
        val proEntitlement = customerInfo.entitlements[PREMIUM_ENTITLEMENT_ID]
        val isPremium = proEntitlement?.isActive == true

        _isPremiumUser.value = isPremium

        // Single greppable confirmation of the client's entitlement decision — mirrors the
        // backend's `pro-status:` lines. Filter with: adb logcat | grep subs-flow
        // IMPORTANT: log the CURRENT app user id (== Firebase UID == the backend's
        // app_user_id), NOT customerInfo.originalAppUserId — the latter returns the
        // historical/original alias (often "$RCAnonymousID:...") and is misleading when
        // checking that client/server identities line up.
        Log.i(
            TAG,
            "subs-flow: entitlement resolved — appUserId=${currentAppUserId(customerInfo)} " +
                "premium=$isPremium expires=${proEntitlement?.expirationDate} " +
                "willRenew=${proEntitlement?.willRenew} " +
                "activeSubs=${customerInfo.activeSubscriptions.joinToString()}"
        )

        Log.d(TAG, "==== Premium Status Update ====")
        Log.d(TAG, "App User ID: ${customerInfo.originalAppUserId}")
        Log.d(TAG, "Premium Status: $isPremium")
        Log.d(TAG, "Expiry Date: ${proEntitlement?.expirationDate}")
        Log.d(TAG, "Will Renew: ${proEntitlement?.willRenew}")
        Log.d(TAG, "Active Subscriptions: ${customerInfo.activeSubscriptions.joinToString()}")
        Log.d(TAG, "============================")
    }

    /**
     * The CURRENT identified app user id (Firebase UID after logIn/configure) — this is what
     * RevenueCat sends as `app_user_id` to the webhook and what the backend keys pro state on.
     * Falls back to the customer's original id only if the SDK isn't configured yet. Use this
     * for identity logging, never [CustomerInfo.originalAppUserId] (the historical alias).
     */
    private fun currentAppUserId(customerInfo: CustomerInfo): String =
        runCatching { Purchases.sharedInstance.appUserID }.getOrDefault(customerInfo.originalAppUserId)

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
package com.kharagedition.tibetankeyboard.data.repository

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.kharagedition.tibetankeyboard.BuildConfig
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.revenuecat.purchases.*
import com.revenuecat.purchases.interfaces.*
import com.revenuecat.purchases.models.GoogleReplacementMode
import com.revenuecat.purchases.models.GoogleSubscriptionOption
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.paywalls.events.CustomPaywallImpressionParams
import com.revenuecat.purchases.interfaces.SyncPurchasesCallback
import com.kharagedition.tibetankeyboard.subscription.OfferingSummary
import com.kharagedition.tibetankeyboard.subscription.PaywallChoice
import com.kharagedition.tibetankeyboard.subscription.PaywallOfferingResolver
import com.kharagedition.tibetankeyboard.subscription.DefaultPlanPolicy
import com.kharagedition.tibetankeyboard.subscription.PlanSummary
import com.kharagedition.tibetankeyboard.subscription.BillingCatalog
import com.kharagedition.tibetankeyboard.subscription.BillingStore
import com.kharagedition.tibetankeyboard.subscription.PlanKind
import com.kharagedition.tibetankeyboard.subscription.RemoteConfig
import com.kharagedition.tibetankeyboard.subscription.WebCheckout
import com.kharagedition.tibetankeyboard.subscription.WebPaywallExperiment
import com.kharagedition.tibetankeyboard.subscription.WebPaywallVariant
import com.kharagedition.tibetankeyboard.subscription.WebPlan
import com.kharagedition.tibetankeyboard.subscription.WebPlans
import com.kharagedition.tibetankeyboard.subscription.ManageSubscriptionPolicy
import com.kharagedition.tibetankeyboard.subscription.SubscriptionSnapshot
import com.kharagedition.tibetankeyboard.billing.BillingAvailability
import com.kharagedition.tibetankeyboard.billing.BillingSignals
import com.kharagedition.tibetankeyboard.data.local.MonetizationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Singleton class to manage RevenueCat subscription logic
 * This handles all subscription-related operations in one place
 */
class RevenueCatManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: RevenueCatManager? = null
        private const val TAG = "RevenueCatManager"

        /** Read by identifier, not `offerings.current`, so shipped clients keep the `sale` offering. */
        private const val OFFERING_ID = BillingCatalog.OFFERING_PRO_V2

        /** RevenueCat stores whose purchases are managed on the web (card checkout). */
        private val WEB_STORES = setOf(Store.RC_BILLING, Store.STRIPE, Store.PADDLE)

        private val OFFERINGS_RETRY_DELAYS_MS = longArrayOf(0L, 1_000L, 3_000L)

        /** Stripe → RevenueCat can land a few seconds after the checkout's success page. */
        private val WEB_PURCHASE_RETRY_DELAYS_MS = longArrayOf(0L, 2_000L, 4_000L)
        private const val LOOKUP_TIMEOUT_MS = 3_000L

        /** Customer attribute holding the side of the card checkout paywall test. */
        private const val ATTRIBUTE_WEB_PAYWALL_VARIANT = "web_paywall_variant"

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

    /** Set on configure; feeds [billingSignals] (SIM country, stored failures) and the debug overrides. */
    @Volatile
    private var appContext: Context? = null

    /** Debug builds only: pretend this PRO account is free, to test the free-user paths. */
    private fun debugForceFree(): Boolean = BuildConfig.DEBUG &&
        appContext?.let { MonetizationStore.getInstance(it).debugForceFree() } == true

    /** Mirrors [Purchases.isConfigured]. */
    private var isConfigured = false
        get() = field || Purchases.isConfigured

    // Seeded false so observers never see null before the first callback.
    private val _isPremiumUser = MutableLiveData<Boolean>(false)

    /**
     * False until RevenueCat has answered once in this process: [isPremiumUser] starts as `false`,
     * which must not be read as "free" (no upsells, no quota for a subscriber at cold start).
     */
    @Volatile
    var isPremiumKnown: Boolean = false
        private set
    val isPremiumUser: LiveData<Boolean> = _isPremiumUser

    /** RevenueCat error code of the last failed offerings load; feeds `BillingAvailability`. */
    @Volatile
    private var lastOfferingsErrorCode: String? = null

    /** Dashboard switches (see [RemoteConfig]); defaults until offerings first load. */
    @Volatile
    var remoteConfig: RemoteConfig = RemoteConfig()
        private set

    /** Card checkout is live in this build (its RevenueCat Web Purchase Link is set). */
    val webCheckoutConfigured: Boolean get() = WebCheckout.isConfigured(BuildConfig.WEB_PURCHASE_LINK)

    /** What the paywall should show for one placement. [offering] is null only for [PaywallChoice.Unavailable]. */
    class ResolvedPaywall(
        val choice: PaywallChoice,
        val offering: Offering?,
        val plans: List<PremiumPlan>,
    )

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
        /** Free-trial length of the plan's default option, null when it has no trial (or none is eligible). */
        val freeTrialDays: Int? = null,
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
        appContext = context.applicationContext
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
        appContext = context.applicationContext
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "subs-flow: no Firebase user — configuring RevenueCat anonymously")
            configureAnonymous(context)
            callback?.onSuccess("Premium services initialized")
            return
        }

        val userId = currentUser.uid
        Log.d(TAG, "Initializing RevenueCat with Firebase UID: $userId")

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
                val errorMsg = "Failed to configure RevenueCat: ${e.message}"
                Log.e(TAG, errorMsg, e)
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
                    val errorMsg = "RevenueCat logIn failed: ${error.message}"
                    Log.e(TAG, errorMsg)
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
                val errorMsg = "Failed to fetch customer info: ${error.message}"
                Log.e(TAG, errorMsg)
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
                // Fall back to `current` so a build shipped before pro_v2 is promoted still sells.
                val offering = offerings.getOffering(OFFERING_ID) ?: offerings.current
                lastOfferingsErrorCode = null
                updateRemoteConfig(offerings)

                when {
                    offering == null -> callback?.onError(ERROR_NO_OFFERING, "Premium subscription not available")
                    offering.availablePackages.isEmpty() ->
                        callback?.onError(ERROR_NO_PACKAGE, "Premium subscription not available")
                    else -> {
                        Log.i(TAG, "subs-flow: ${offering.availablePackages.size} package(s) in '${offering.identifier}'")
                        callback?.onSuccess("Premium services initialized")
                    }
                }
            }

            override fun onError(error: PurchasesError) {
                lastOfferingsErrorCode = error.code.name
                val errorMsg = "Failed to load premium options: ${error.message}"
                callback?.onError(error.code.name, errorMsg)
            }
        })
    }

    private fun updateRemoteConfig(offerings: Offerings) {
        val meta = offerings.getOffering(OFFERING_ID)?.metadata ?: return
        remoteConfig = RemoteConfig.fromMetadata(meta)
    }

    /**
     * Fresh offerings for a paywall that is opening, retried because the one-shot fetch at app
     * start leaves the paywall stuck on "Loading plans…" if it happened offline. Null when
     * unconfigured or every attempt failed; [lastOfferingsErrorCode] then says why.
     */
    suspend fun loadOfferings(): Offerings? {
        if (!isConfigured) return null
        for (wait in OFFERINGS_RETRY_DELAYS_MS) {
            if (wait > 0) delay(wait)
            try {
                val offerings = Purchases.sharedInstance.awaitOfferings()
                lastOfferingsErrorCode = null
                updateRemoteConfig(offerings)
                return offerings
            } catch (e: PurchasesException) {
                lastOfferingsErrorCode = e.error.code.name
                Log.w(TAG, "subs-flow: offerings load failed (${e.error.code}) — ${e.error.message}")
                // No product exists for this store account; retrying can't change that.
                if (e.error.code == PurchasesErrorCode.ConfigurationError) return null
            }
        }
        return null
    }

    /**
     * Which paywall to show for [placementId]. The placement's own [Offering] object is kept (not
     * re-read by id) so RevenueCat attributes the purchase to the placement and targeting rule.
     */
    suspend fun resolvePaywall(placementId: String, storefront: String?): ResolvedPaywall {
        val offerings = loadOfferings()
        val placementOffering = offerings?.getCurrentOfferingForPlacement(placementId)
        val current = offerings?.current
        val fallback = offerings?.getOffering(OFFERING_ID)
        // Debug builds can ask for our own paywall: without the dashboard's offerings the
        // resolver falls back to it.
        val own = debugOwnPaywall()
        val choice = PaywallOfferingResolver.resolve(
            placementOffering?.summary().takeUnless { own },
            fallback?.summary(),
            current?.summary().takeUnless { own },
        )
        val chosenId = choiceOfferingId(choice)
        val offering = listOfNotNull(placementOffering, current, fallback).firstOrNull { it.identifier == chosenId }
        val plans = offering?.let { withDefault(buildPlans(it), storefront) }.orEmpty()
        Log.i(
            TAG,
            "subs-flow: paywall for '$placementId' — placement=${placementOffering?.identifier} " +
                "current=${current?.identifier} → $choice",
        )
        return ResolvedPaywall(choice, offering, plans)
    }

    private fun choiceOfferingId(choice: PaywallChoice): String? = when (choice) {
        is PaywallChoice.Dashboard -> choice.offeringId
        is PaywallChoice.Custom -> choice.offeringId
        PaywallChoice.Unavailable -> null
    }

    /** Whole days in a free-trial period (`Period.valueInDays` is internal SDK API). */
    private fun approxDays(period: Period): Int = when (period.unit) {
        Period.Unit.DAY -> period.value
        Period.Unit.WEEK -> period.value * 7
        Period.Unit.MONTH -> period.value * 30
        Period.Unit.YEAR -> period.value * 365
        else -> 0
    }

    private fun Offering.summary() = OfferingSummary(identifier, hasPaywall, availablePackages.size)

    /** Mark the plan [DefaultPlanPolicy] picks as the recommended (pre-selected) one. */
    private fun withDefault(plans: List<PremiumPlan>, storefront: String?): List<PremiumPlan> {
        val pick = DefaultPlanPolicy.pick(
            plans.map { PlanSummary(it.id, it.analyticsPlan, it.freeTrialDays != null) },
            storefront,
        ) ?: return plans
        return plans.map { it.copy(isRecommended = it.id == pick.id) }
    }

    /** PRO status straight from RevenueCat, or null if it can't be known within a few seconds. */
    suspend fun awaitIsPremium(): Boolean? {
        if (!isConfigured) return null
        return withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
            // A failed lookup is "unknown", never "not premium": callers must not upsell a
            // subscriber just because RevenueCat was unreachable.
            val info = attempt { Purchases.sharedInstance.awaitCustomerInfo() } ?: return@withTimeoutOrNull null
            updatePremiumStatus(info)
            info.hasPro()
        }
    }

    /**
     * Tells RevenueCat our own paywall was shown. Its dashboard paywalls count views themselves;
     * without this, experiments comparing them to [offeringId]'s custom screen can't see its views.
     */
    fun trackCustomPaywallImpression(offeringId: String?) {
        if (!isConfigured) return
        runCatching {
            Purchases.sharedInstance.trackCustomPaywallImpression(
                CustomPaywallImpressionParams(paywallId = BillingCatalog.CUSTOM_PAYWALL_ID, offeringId = offeringId)
            )
        }
    }

    /** The user's Google Play country (e.g. "IN", "BT"), or null if Play didn't answer in time. */
    suspend fun storefrontCountry(): String? {
        cachedStorefront()?.let { return it }
        if (!isConfigured) return null
        return withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
            attempt { Purchases.sharedInstance.awaitStorefrontCountryCode() }
        }
    }

    /** The Play country if already known, without waiting — safe on every keystroke. */
    fun cachedStorefront(): String? {
        debugStorefront()?.let { return it }
        return if (isConfigured) runCatching { Purchases.sharedInstance.storefrontCountryCode }.getOrNull() else null
    }

    /** Debug builds only: our own paywall was asked for through `applyDebugMonetizationOverrides`. */
    private fun debugOwnPaywall(): Boolean =
        BuildConfig.DEBUG && appContext?.let { MonetizationStore.getInstance(it).debugOwnPaywall() } == true

    /** Debug builds only: a storefront faked through `applyDebugMonetizationOverrides`. */
    private fun debugStorefront(): String? = if (BuildConfig.DEBUG) {
        appContext?.let { MonetizationStore.getInstance(it).debugStorefrontOverride() }
    } else null

    /** Everything [BillingAvailability] needs to know about this user, for [storefront]. */
    fun billingSignals(storefront: String?): BillingSignals {
        val context = appContext
        val store = context?.let { MonetizationStore.getInstance(it) }
        return BillingSignals(
            storefront = storefront,
            simMcc = context?.resources?.configuration?.mcc ?: 0,
            offeringsErrorCode = lastOfferingsErrorCode,
            lastHardFailureCode = store?.lastHardFailureCode(),
            lastHardFailureAtMs = store?.lastHardFailureAtMs() ?: 0L,
            nowMs = System.currentTimeMillis(),
            webCheckoutCountries = remoteConfig.webCheckoutCountries,
        )
    }

    /**
     * Whether this user can buy PRO at all, from what is already known (no network). False only
     * where Play can't sell and card checkout isn't live yet — then upsells must stay quiet.
     */
    fun canSellHere(storefront: String? = cachedStorefront()): Boolean = BillingAvailability.canSell(
        BillingAvailability.route(billingSignals(storefront)),
        webCheckoutConfigured = webCheckoutConfigured,
    )

    /** The card checkout's plans, at the dashboard's prices where it states them. */
    fun webPlans(): List<WebPlan> = WebPlans.list(remoteConfig.webPrices)

    /** This install's side of the card checkout paywall test; null before [configureAnonymous]. */
    fun webPaywallVariant(): WebPaywallVariant? {
        val context = appContext ?: return null
        return WebPaywallExperiment.variant(
            MonetizationStore.getInstance(context).webPaywallBucket(),
            remoteConfig.webAnnualFirstPercent,
        )
    }

    /**
     * Notes [variant] on the RevenueCat customer. Card payments never pass through the app, so
     * this is what lets revenue be compared by variant.
     */
    fun reportWebPaywallVariant(variant: WebPaywallVariant) {
        if (!isConfigured) return
        runCatching {
            Purchases.sharedInstance.setAttributes(mapOf(ATTRIBUTE_WEB_PAYWALL_VARIANT to variant.label))
        }
    }

    /**
     * Whether this customer never bought anything, which is who the card checkout gives its free
     * trial to. Null when RevenueCat can't say; the paywall then promises no trial.
     */
    suspend fun awaitNeverPurchased(): Boolean? {
        if (!isConfigured) return null
        return withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
            identifySignedInUser()
            attempt { Purchases.sharedInstance.awaitCustomerInfo() }?.allPurchasedProductIds?.isEmpty()
        }
    }

    /** RevenueCat must be the signed-in user before it is asked about them; sign-in's `logIn` may still be in flight. */
    private suspend fun identifySignedInUser() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (Purchases.sharedInstance.appUserID != uid) attempt { Purchases.sharedInstance.awaitLogIn(uid) }
    }

    /**
     * A purchase made through RevenueCat's own paywall UI never passes through [purchasePremium],
     * so publish the new entitlement here. (RevenueCat acknowledges it with Play itself.)
     */
    fun onPaywallPurchaseCompleted(customerInfo: CustomerInfo) {
        Log.i(TAG, "subs-flow: paywall purchase completed — appUserId=${currentAppUserId(customerInfo)}")
        updatePremiumStatus(customerInfo)
    }

    /**
     * After the card checkout tab closes: is PRO active now? The checkout link carries the
     * Firebase UID, so RevenueCat must be that user before asking, and Stripe's result can take a
     * few seconds to land, so it asks a few times.
     */
    suspend fun refreshAfterWebCheckout(): Boolean {
        if (!isConfigured) return false
        identifySignedInUser()
        for (wait in WEB_PURCHASE_RETRY_DELAYS_MS) {
            if (wait > 0) delay(wait)
            Purchases.sharedInstance.invalidateCustomerInfoCache() // the cached one predates the purchase
            val info = attempt { Purchases.sharedInstance.awaitCustomerInfo(CacheFetchPolicy.FETCH_CURRENT) } ?: continue
            updatePremiumStatus(info)
            if (info.hasPro()) return true
        }
        return false
    }

    /** A discount a subscriber can take instead of cancelling (Google Play only). */
    class RetentionOffer(
        internal val option: GoogleSubscriptionOption,
        val discountedPrice: String,
        val fullPrice: String,
        val discountedMonths: Int,
        val percentOff: Int,
    )

    sealed interface RetentionResult {
        data object Accepted : RetentionResult
        data object Cancelled : RetentionResult
        data class Failed(val code: String) : RetentionResult
    }

    /**
     * The user's PRO as the manage screen shows it, or null if RevenueCat can't be reached.
     * [fresh] skips the cache — used after the user returns from Google Play.
     */
    suspend fun awaitSubscriptionSnapshot(fresh: Boolean = false): SubscriptionSnapshot? {
        if (!isConfigured) return null
        if (fresh) Purchases.sharedInstance.invalidateCustomerInfoCache()
        val info = attempt {
            Purchases.sharedInstance.awaitCustomerInfo(
                if (fresh) CacheFetchPolicy.FETCH_CURRENT else CacheFetchPolicy.default()
            )
        } ?: return null
        updatePremiumStatus(info)
        val pro = info.entitlements[BillingCatalog.ENTITLEMENT_PRO]
            ?: return SubscriptionSnapshot(
                plan = PlanKind.OTHER,
                store = BillingStore.OTHER, productId = null, isActive = false, willRenew = false,
                inTrial = false, hasBillingIssue = false, expiresAtMs = null, price = null,
                managementUrl = info.managementURL?.toString(),
            )
        // Keyed by product id; Play subscriptions may carry the base plan too.
        val subs = info.subscriptionsByProductIdentifier
        val sub = subs[pro.productIdentifier]
            ?: pro.productPlanIdentifier?.let { subs["${pro.productIdentifier}:$it"] }
            ?: subs.values.firstOrNull { it.productIdentifier.substringBefore(':') == pro.productIdentifier }
        val expires = pro.expirationDate?.time
        return SubscriptionSnapshot(
            plan = SubscriptionSnapshot.planOf(pro.productIdentifier, expires),
            store = when {
                pro.store == Store.PLAY_STORE -> BillingStore.PLAY
                pro.store == Store.PROMOTIONAL -> BillingStore.GRANTED
                pro.store in WEB_STORES -> BillingStore.WEB
                else -> BillingStore.OTHER
            },
            productId = pro.productIdentifier,
            isActive = pro.isActive,
            willRenew = pro.willRenew,
            inTrial = pro.periodType == PeriodType.TRIAL,
            hasBillingIssue = pro.billingIssueDetectedAt != null,
            expiresAtMs = expires,
            price = sub?.price?.formatted,
            managementUrl = (sub?.managementURL ?: info.managementURL)?.toString(),
            basePlanId = pro.productPlanIdentifier,
        )
    }

    /**
     * The retention offer for [productId] with its prices, or null if Play has none that this
     * subscriber, on [currentBasePlanId], can take (see [ManageSubscriptionPolicy.offerApplies]).
     */
    suspend fun loadRetentionOffer(productId: String, currentBasePlanId: String?): RetentionOffer? {
        if (!isConfigured) return null
        val products = attempt { Purchases.sharedInstance.awaitGetProducts(listOf(productId), ProductType.SUBS) }
            ?: return null
        val options = products
            .flatMap { it.subscriptionOptions.orEmpty() }
            .filterIsInstance<GoogleSubscriptionOption>()
            .filter { it.offerId == BillingCatalog.RETENTION_OFFER_ID }
        val applies = ManageSubscriptionPolicy.offerApplies(currentBasePlanId, options.map { it.basePlanId })
        Log.i(
            TAG,
            "subs-flow: retention offer for $productId on ${options.map { it.basePlanId }} " +
                "(current plan $currentBasePlanId) applies=$applies",
        )
        if (!applies) return null
        val option = options.first { it.basePlanId != currentBasePlanId }
        val full = option.pricingPhases.lastOrNull() ?: return null
        val discounted = option.pricingPhases.firstOrNull {
            it.price.amountMicros in 1 until full.price.amountMicros
        } ?: return null
        val pct = ManageSubscriptionPolicy.percentOff(discounted.price.amountMicros, full.price.amountMicros)
            ?: return null
        return RetentionOffer(
            option = option,
            discountedPrice = discounted.price.formatted,
            fullPrice = full.price.formatted,
            discountedMonths = discounted.billingCycleCount ?: 1,
            percentOff = pct,
        )
    }

    /**
     * Moves the current subscription onto [offer]. It must be a plan change (`oldProductId`), not
     * a plain purchase: a plain purchase re-sends the account id, and Play rejects it for anyone
     * whose subscription was bought under another app user id ("Account identifiers don't match
     * the previous subscription"). RevenueCat's Customer Center makes that plain purchase, which is
     * why its offer failed. WITHOUT_PRORATION keeps the paid month and bills the discount from the
     * next renewal.
     */
    suspend fun purchaseRetentionOffer(activity: Activity, offer: RetentionOffer): RetentionResult {
        if (!isConfigured) return RetentionResult.Failed(ERROR_NOT_CONFIGURED)
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(
                PurchaseParams.Builder(activity, offer.option)
                    .oldProductId(offer.option.productId)
                    .googleReplacementMode(GoogleReplacementMode.WITHOUT_PRORATION)
                    .build()
            )
            Log.i(TAG, "subs-flow: retention offer accepted — order=${result.storeTransaction.orderId}")
            updatePremiumStatus(result.customerInfo)
            RetentionResult.Accepted
        } catch (e: PurchasesTransactionException) {
            Log.w(TAG, "subs-flow: retention offer failed — ${e.code} ${e.underlyingErrorMessage}")
            if (e.userCancelled) RetentionResult.Cancelled else RetentionResult.Failed(e.code.name)
        } catch (e: PurchasesException) {
            RetentionResult.Failed(e.code.name)
        }
    }

    /** Restore: true if PRO is active afterwards, false if nothing was found, null on failure. */
    suspend fun restorePurchases(): Boolean? {
        if (!isConfigured) return null
        val info = attempt { Purchases.sharedInstance.awaitRestore() } ?: return null
        updatePremiumStatus(info)
        return info.hasPro()
    }

    /** PRO per [info], honouring the debug "force free" override like everything else here. */
    private fun CustomerInfo.hasPro(): Boolean =
        entitlements[BillingCatalog.ENTITLEMENT_PRO]?.isActive == true && !debugForceFree()

    /**
     * [block]'s result, or null if it failed. Unlike `runCatching`, a coroutine cancellation is
     * rethrown rather than swallowed as a failure.
     */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "subs-flow: RevenueCat call failed — ${e.message}")
        null
    }

    /** The RevenueCat App User ID, or null when not configured. */
    fun appUserIdOrNull(): String? =
        if (isConfigured) runCatching { Purchases.sharedInstance.appUserID }.getOrNull() else null

    /** The [AppAnalytics.Plan] a package sells, from its package type. */
    fun analyticsPlanOf(pkg: Package): String = when (pkg.packageType) {
        PackageType.ANNUAL -> AppAnalytics.Plan.ANNUAL
        PackageType.MONTHLY -> AppAnalytics.Plan.MONTHLY
        PackageType.LIFETIME -> AppAnalytics.Plan.LIFETIME
        else -> AppAnalytics.Plan.UNKNOWN
    }

    /**
     * Flatten an [Offering] into plans, annual → monthly → lifetime (any other package after). The
     * saving uses real per-month prices — Play's regional tiers are not a constant multiple. Which
     * plan is pre-selected is decided later, by [withDefault].
     */
    private fun buildPlans(offering: Offering): List<PremiumPlan> {
        val order = listOf(AppAnalytics.Plan.ANNUAL, AppAnalytics.Plan.MONTHLY, AppAnalytics.Plan.LIFETIME)
        // The legacy `sale` offering types its monthly product as `$rc_weekly`, so it lands in
        // UNKNOWN; it is still listed rather than showing nothing.
        val packages = offering.availablePackages.sortedBy { order.indexOf(analyticsPlanOf(it)).let { i -> if (i < 0) order.size else i } }

        val monthlyMicros = offering.monthly?.product?.price?.amountMicros
        val annualPerMonthMicros = offering.annual?.product?.pricePerMonth()?.amountMicros
        val saving = if (monthlyMicros != null && annualPerMonthMicros != null && monthlyMicros > 0) {
            (100 - (annualPerMonthMicros * 100 / monthlyMicros)).toInt().takeIf { it > 0 }
        } else null

        return packages.map { p ->
            val analytics = analyticsPlanOf(p)
            PremiumPlan(
                id = p.identifier,
                analyticsPlan = analytics,
                title = p.product.title,
                price = p.product.price.formatted,
                pricePerMonth = if (analytics == AppAnalytics.Plan.LIFETIME) null else p.product.formattedPricePerMonth(),
                savingPercent = if (analytics == AppAnalytics.Plan.ANNUAL) saving else null,
                isRecommended = false,
                priceAmount = p.product.price.amountMicros / 1_000_000.0,
                currencyCode = p.product.price.currencyCode,
                freeTrialDays = p.product.defaultOption?.freePhase?.billingPeriod?.let(::approxDays)?.takeIf { it > 0 },
                rcPackage = p,
            )
        }
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

        // CRITICAL: First sync purchases to acknowledge any pending subscriptions
        syncPurchasesWithGooglePlay()

        Purchases.sharedInstance.getCustomerInfo(
            object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    Log.d(TAG, "Customer info refreshed - User ID: ${customerInfo.originalAppUserId}")
                    updatePremiumStatus(customerInfo)
                    callback?.onSuccess("Premium status updated")
                }

                override fun onError(error: PurchasesError) {
                    val errorMsg = "Failed to load premium status: ${error.message}"
                    Log.e(TAG, errorMsg)
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

    /** Buy [plan]. No Firebase user required — see [configureAnonymous]. */
    fun purchasePremium(
        activity: Activity,
        plan: PremiumPlan,
        callback: SubscriptionCallback,
    ) {
        if (!isConfigured) {
            // Configured at app start, so this is a genuine failure — never ask the user to log in.
            val errorMsg = "Premium is still starting up. Please try again in a moment."
            Log.e(TAG, "subs-flow: purchase blocked — SDK not configured")
            callback.onError(ERROR_NOT_CONFIGURED, errorMsg)
            return
        }

        val packageToPurchase = plan.rcPackage

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        Log.d(TAG, "==== Starting Purchase ====")
        Log.d(TAG, "Firebase User ID: $userId")
        Log.d(TAG, "Package: ${packageToPurchase.identifier}")
        Log.d(TAG, "Product: ${packageToPurchase.product.id}")
        Log.d(TAG, "========================")
        val purchaseParams = PurchaseParams.Builder(activity, packageToPurchase).build()

        Purchases.sharedInstance.purchase(
            purchaseParams,
            object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
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
        val proEntitlement = customerInfo.entitlements[BillingCatalog.ENTITLEMENT_PRO]
        val isPremium = customerInfo.hasPro()

        isPremiumKnown = true
        _isPremiumUser.setOrPost(isPremium)

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

    /** setValue on the main thread (observers see it at once), postValue from anywhere else. */
    private fun <T> MutableLiveData<T>.setOrPost(value: T) {
        if (Looper.myLooper() == Looper.getMainLooper()) this.value = value else postValue(value)
    }

    /**
     * Check if user is premium without triggering network call
     */
    fun isPremiumUserCached(): Boolean {
        return _isPremiumUser.value ?: false
    }
}
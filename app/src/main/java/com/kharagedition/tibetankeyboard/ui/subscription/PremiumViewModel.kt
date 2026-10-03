package com.kharagedition.tibetankeyboard.ui.subscription

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.kharagedition.tibetankeyboard.BuildConfig
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.billing.BillingAvailability
import com.kharagedition.tibetankeyboard.billing.BillingDecision
import com.kharagedition.tibetankeyboard.billing.BillingRoute
import com.kharagedition.tibetankeyboard.data.local.MonetizationStore
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.subscription.PaywallChoice
import com.kharagedition.tibetankeyboard.subscription.PaywallPlacements
import com.kharagedition.tibetankeyboard.subscription.WebCheckout
import com.kharagedition.tibetankeyboard.subscription.WebPaywallVariant
import com.kharagedition.tibetankeyboard.subscription.WebPlan
import com.kharagedition.tibetankeyboard.subscription.WebPlans
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which paywall the screen renders. */
sealed interface PaywallContent {
    data object Loading : PaywallContent

    /** A paywall designed in the RevenueCat dashboard. */
    data class Dashboard(val offering: Offering) : PaywallContent

    /** Our own Compose paywall, listing [PremiumUiState.plans]. */
    data object Custom : PaywallContent

    /** Card checkout, only where Google Play can't sell (see [BillingAvailability]). */
    data object WebCheckout : PaywallContent

    /** Nothing could be loaded; the screen offers a retry. */
    data object Error : PaywallContent
}

/** What the paywall renders. */
data class PremiumUiState(
    val content: PaywallContent = PaywallContent.Loading,
    val plans: List<RevenueCatManager.PremiumPlan> = emptyList(),
    val selectedPlanId: String? = null,
    val isPremium: Boolean = false,
    val isPurchasing: Boolean = false,
    val billingRoute: BillingRoute = BillingRoute.PLAY,
    val placementId: String = PaywallPlacements.DEFAULT,
    val offeringId: String? = null,
    val isSignedIn: Boolean = false,
    /** False until the card checkout link is configured for this build. */
    val webCheckoutAvailable: Boolean = false,
    /** Card checkout: asking RevenueCat whether the payment has landed. */
    val checkingWebPurchase: Boolean = false,
    /** Card checkout: the last check found no payment yet (it can take a minute). */
    val webPurchasePending: Boolean = false,
    /** Card checkout: the plans of the in-app picker. */
    val webPlans: List<WebPlan> = emptyList(),
    val selectedWebPlanId: String? = null,
    /** Card checkout: what a year saves over twelve months, in percent. */
    val webAnnualSaving: Int? = null,
    /** Card checkout: this customer never bought anything, so the checkout starts with the free trial. */
    val webTrialEligible: Boolean = false,
) {
    val selectedPlan: RevenueCatManager.PremiumPlan?
        get() = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull()

    val selectedWebPlan: WebPlan?
        get() = webPlans.firstOrNull { it.packageId == selectedWebPlanId } ?: webPlans.firstOrNull()
}

/** Lambdas the paywall can invoke; mirrors the `XxxActions` convention of the other screens. */
class PremiumActions(
    val onBack: () -> Unit = {},
    val onSelectPlan: (RevenueCatManager.PremiumPlan) -> Unit = {},
    val onPurchase: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** One-off things for the Activity to do. */
sealed interface PremiumEvent {
    data class Message(@StringRes val text: Int) : PremiumEvent

    /**
     * PRO is active: close, without counting it as an abandoned paywall. [announce] after a
     * purchase or restore in this paywall; not for someone who already had PRO.
     */
    data class ProActive(val announce: Boolean) : PremiumEvent

    /** Back from the sign-in that Continue asked for: open the card checkout. */
    data object OpenWebCheckout : PremiumEvent
}

/**
 * Paywall state and funnel. Resolves, per entry point, which paywall to show (a RevenueCat
 * dashboard paywall, our own screen, or card checkout where Play can't sell), and owns the
 * purchase analytics so they survive rotation. The Activity only launches what needs it.
 */
class PremiumViewModel(
    app: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {

    private val revenueCat = RevenueCatManager.getInstance()
    private val store = MonetizationStore.getInstance(app)

    /** The [AppAnalytics.UpgradeSource] that opened this paywall. */
    val source: String =
        savedStateHandle.get<String>(PremiumActivity.EXTRA_UPGRADE_SOURCE) ?: AppAnalytics.UpgradeSource.UNKNOWN

    private val _uiState = MutableStateFlow(
        PremiumUiState(
            placementId = PaywallPlacements.forSource(source),
            isSignedIn = isSignedIn(),
            webCheckoutAvailable = revenueCat.webCheckoutConfigured,
            // Kept in the saved state: the checkout is a browser tab, and Android may end this
            // process while the user types their card number there.
            selectedWebPlanId = savedStateHandle[KEY_WEB_PLAN],
        ).withWebPlans()
    )
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    private val _events = Channel<PremiumEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var storefront: String? = null
    private var billingUnavailableLogged = false
    private var loadJob: Job? = null

    /** Plan bought through the dashboard paywall, kept for the completed event. */
    private var dashboardPackage: Package? = null

    /** This install's side of the card checkout paywall test, once that paywall shows. */
    private var webVariant: WebPaywallVariant? = null

    /** Back from signing in to pay: open the checkout once the account is checked. */
    private var autoContinuePending = false

    private val premiumObserver = Observer<Boolean> { isPremium ->
        _uiState.update { it.copy(isPremium = isPremium) }
        if (isPremium) _events.trySend(PremiumEvent.ProActive(announce = false))
    }

    init {
        revenueCat.isPremiumUser.observeForever(premiumObserver)
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(content = PaywallContent.Loading) }
            val premium = async { revenueCat.awaitIsPremium() }
            val country = currentStorefront()
            storefront = country
            // Card checkout needs none of Play's products, and Play is slowest to answer exactly
            // where it can't sell (in Bhutan nearly half the paywalls closed before loading ended).
            val known = billingDecision()
            val webFirst = known.route == BillingRoute.WEB_CHECKOUT
            val resolved = if (webFirst) {
                RevenueCatManager.ResolvedPaywall(PaywallChoice.Unavailable, null, emptyList())
            } else {
                revenueCat.resolvePaywall(_uiState.value.placementId, country)
            }
            // A failed offerings load can itself prove Play can't sell (Bhutanese SIM, no storefront).
            val decision = if (webFirst) known else billingDecision()
            val web = decision.route == BillingRoute.WEB_CHECKOUT
            val trialLookup = if (web) async { enterWebCheckout() } else null
            val isPremium = premium.await()

            val content = when {
                decision.route == BillingRoute.WEB_CHECKOUT -> PaywallContent.WebCheckout
                resolved.choice is PaywallChoice.Dashboard && resolved.offering != null ->
                    PaywallContent.Dashboard(resolved.offering)
                resolved.choice is PaywallChoice.Custom && resolved.plans.isNotEmpty() -> PaywallContent.Custom
                else -> PaywallContent.Error
            }
            if (web) logBillingUnavailableOnce(decision)
            val trialEligible = trialLookup?.await() == true

            _uiState.update { state ->
                state.withWebPlans().copy(
                    content = content,
                    plans = resolved.plans,
                    // Keep the user's choice across reloads, else the policy's pick.
                    selectedPlanId = state.selectedPlanId?.takeIf { id -> resolved.plans.any { it.id == id } }
                        ?: resolved.plans.firstOrNull { it.isRecommended }?.id
                        ?: resolved.plans.firstOrNull()?.id,
                    isPremium = isPremium ?: state.isPremium,
                    billingRoute = decision.route,
                    offeringId = resolved.offering?.identifier,
                    isSignedIn = isSignedIn(),
                    webTrialEligible = trialEligible,
                )
            }
            // Unknown PRO status after the timeout still counts: better one view logged for a
            // subscriber than none for everyone offline. Known subscribers close instead.
            if (isPremium != true) logViewOnce(content)
        }
    }

    /** `paywall_viewed` once per paywall (survives rotation and process death). */
    private fun logViewOnce(content: PaywallContent) {
        if (content == PaywallContent.Loading || content == PaywallContent.Error) return
        if (savedStateHandle.get<Boolean>(KEY_VIEW_LOGGED) == true) return
        savedStateHandle[KEY_VIEW_LOGGED] = true
        val state = _uiState.value
        AppAnalytics.logPaywallViewed(
            source, state.placementId, state.offeringId, paywallType(content),
            variant = webVariant?.label.takeIf { content == PaywallContent.WebCheckout },
        )
        // Dashboard paywalls report their own views to RevenueCat; ours must.
        if (content == PaywallContent.Custom) revenueCat.trackCustomPaywallImpression(state.offeringId)
    }

    fun selectPlan(plan: RevenueCatManager.PremiumPlan) {
        _uiState.update { it.copy(selectedPlanId = plan.id) }
        AppAnalytics.logPaywallPlanSelected(source, plan.analyticsPlan)
    }

    // ── our paywall's purchase (the Activity launches Play) ─────────────────

    fun onPurchaseStarted(plan: RevenueCatManager.PremiumPlan) {
        AppAnalytics.logPurchaseStarted(source, plan.analyticsPlan)
        _uiState.update { it.copy(isPurchasing = true) }
    }

    fun onPurchaseSucceeded(plan: RevenueCatManager.PremiumPlan) {
        _uiState.update { it.copy(isPurchasing = false) }
        AppAnalytics.logPurchaseCompleted(source, plan.analyticsPlan, plan.priceAmount, plan.currencyCode)
        _events.trySend(PremiumEvent.ProActive(announce = true))
    }

    fun onPurchaseError(plan: RevenueCatManager.PremiumPlan?, code: String, message: String) {
        _uiState.update { it.copy(isPurchasing = false) }
        AppAnalytics.logPurchaseFailed(source, plan?.analyticsPlan ?: AppAnalytics.Plan.UNKNOWN, code, message)
        onHardFailure(code)
    }

    fun onPurchaseCancelled(plan: RevenueCatManager.PremiumPlan?) {
        _uiState.update { it.copy(isPurchasing = false) }
        AppAnalytics.logPurchaseCancelled(source, plan?.analyticsPlan ?: AppAnalytics.Plan.UNKNOWN)
    }

    // ── RevenueCat's paywall (it does the buying; we keep the GA4 funnel) ───

    fun onDashboardPurchaseStarted(pkg: Package) {
        dashboardPackage = pkg
        AppAnalytics.logPurchaseStarted(source, revenueCat.analyticsPlanOf(pkg))
    }

    fun onDashboardPurchaseCompleted(customerInfo: CustomerInfo) {
        val pkg = dashboardPackage
        AppAnalytics.logPurchaseCompleted(
            source = source,
            plan = pkg?.let(revenueCat::analyticsPlanOf) ?: AppAnalytics.Plan.UNKNOWN,
            price = pkg?.product?.price?.amountMicros?.div(1_000_000.0),
            currency = pkg?.product?.price?.currencyCode,
        )
        revenueCat.onPaywallPurchaseCompleted(customerInfo)
        _events.trySend(PremiumEvent.ProActive(announce = true))
    }

    fun onDashboardPurchaseError(code: String, message: String) {
        AppAnalytics.logPurchaseFailed(source, dashboardPlan(), code, message)
        onHardFailure(code)
    }

    fun onDashboardPurchaseCancelled() = AppAnalytics.logPurchaseCancelled(source, dashboardPlan())

    private fun dashboardPlan(): String = dashboardPackage?.let(revenueCat::analyticsPlanOf) ?: AppAnalytics.Plan.UNKNOWN

    /** Restore from either paywall; says whether anything was found. */
    fun restore() {
        viewModelScope.launch {
            when (revenueCat.restorePurchases()) {
                true -> {
                    AppAnalytics.logPurchaseRestored()
                    _events.trySend(PremiumEvent.ProActive(announce = true))
                }
                false -> _events.trySend(PremiumEvent.Message(R.string.sub_manage_restore_none))
                null -> {
                    AppAnalytics.logRestoreFailed(RevenueCatManager.ERROR_UNKNOWN, "restore failed")
                    _events.trySend(PremiumEvent.Message(R.string.sub_manage_restore_failed))
                }
            }
        }
    }

    /**
     * A purchase failed in a way that means Play can't sell to this account. Remember it, and if
     * this user is in Bhutan, switch them to card checkout instead of letting them retry forever.
     */
    private fun onHardFailure(code: String) {
        if (!BillingAvailability.isHardFailure(code)) return
        store.recordHardFailure(code)
        val decision = billingDecision()
        if (decision.route == BillingRoute.WEB_CHECKOUT) {
            logBillingUnavailableOnce(decision)
            viewModelScope.launch {
                val trialEligible = enterWebCheckout()
                _uiState.update {
                    it.withWebPlans().copy(
                        content = PaywallContent.WebCheckout,
                        billingRoute = BillingRoute.WEB_CHECKOUT,
                        webTrialEligible = trialEligible,
                    )
                }
            }
        }
    }

    // ── card checkout ───────────────────────────────────────────────────────

    /**
     * The card checkout paywall is about to show: settles this install's side of its test, and
     * returns whether the customer who will pay gets the free trial. That customer is the
     * signed-in account, so this runs again after sign-in.
     */
    private suspend fun enterWebCheckout(): Boolean {
        val variant = webVariant ?: revenueCat.webPaywallVariant()?.also {
            webVariant = it
            AppAnalytics.setWebPaywallVariant(it.label)
        }
        val neverPurchased = revenueCat.awaitNeverPurchased()
        // After the lookup, which makes RevenueCat the signed-in user: the payment will be theirs,
        // so the variant must be noted on them.
        variant?.let(revenueCat::reportWebPaywallVariant)
        return neverPurchased == true
    }

    /** The picker's plans; the user's pick is kept, else the variant's plan is pre-selected. */
    private fun PremiumUiState.withWebPlans(): PremiumUiState {
        val plans = revenueCat.webPlans()
        val picked = selectedWebPlanId?.takeIf { id -> plans.any { it.packageId == id } }
            ?: webVariant?.let { WebPlans.preselected(plans, it) }?.packageId
        if (picked != null) savedStateHandle[KEY_WEB_PLAN] = picked
        return copy(
            webPlans = plans,
            selectedWebPlanId = picked,
            webAnnualSaving = WebPlans.annualSavingPercent(plans),
        )
    }

    fun selectWebPlan(plan: WebPlan) {
        savedStateHandle[KEY_WEB_PLAN] = plan.packageId
        _uiState.update { it.copy(selectedWebPlanId = plan.packageId) }
        AppAnalytics.logPaywallPlanSelected(source, plan.plan)
    }

    /**
     * The card checkout's Continue was tapped. Signed out, the Activity sends the user to sign in,
     * and [refreshSignIn] carries on to the checkout when they come back signed in.
     */
    fun onWebCheckoutContinue(signedIn: Boolean) {
        autoContinuePending = false // tapped while the return from sign-in was still checking
        savedStateHandle[KEY_CONTINUE_AFTER_SIGN_IN] = !signedIn
        AppAnalytics.logWebCheckoutContinue(source, webPlanLabel(), webVariantLabel(), signedIn)
    }

    /** Re-read the sign-in state after returning from the login screen. */
    fun refreshSignIn() {
        val signedIn = isSignedIn()
        val justSignedIn = signedIn && !_uiState.value.isSignedIn
        _uiState.update { it.copy(isSignedIn = signedIn) }
        // Only the return from that sign-in continues; backing out of it cancels.
        val continueToCheckout = savedStateHandle.remove<Boolean>(KEY_CONTINUE_AFTER_SIGN_IN) == true && signedIn
        // The account may have bought before, on another phone; the checkout then gives no trial.
        if ((justSignedIn || continueToCheckout) && _uiState.value.content == PaywallContent.WebCheckout) {
            autoContinuePending = continueToCheckout
            viewModelScope.launch {
                val trialEligible = enterWebCheckout()
                _uiState.update { it.copy(webTrialEligible = trialEligible) }
                // They signed in to pay, so don't make them tap Continue again, unless the
                // account already has PRO (the premium observer then closes the paywall).
                if (continueToCheckout && revenueCat.awaitIsPremium() != true && autoContinuePending) {
                    autoContinuePending = false
                    _events.trySend(PremiumEvent.OpenWebCheckout)
                }
            }
        }
    }

    /** The checkout link for this user and the picked plan, or null if signed out or not configured. */
    fun webCheckoutUrl(): String? = WebCheckout.url(
        BuildConfig.WEB_PURCHASE_LINK,
        FirebaseAuth.getInstance().currentUser?.uid,
        _uiState.value.selectedWebPlan?.packageId,
    )

    fun onWebCheckoutOpened() = AppAnalytics.logWebCheckoutOpened(source, webPlanLabel(), webVariantLabel())

    /** After the checkout tab closes (or "check again"): has the payment landed? */
    fun checkWebPurchase() {
        if (_uiState.value.checkingWebPurchase) return
        viewModelScope.launch {
            _uiState.update { it.copy(checkingWebPurchase = true) }
            val paid = revenueCat.refreshAfterWebCheckout()
            _uiState.update { it.copy(checkingWebPurchase = false, webPurchasePending = !paid) }
            if (paid) {
                AppAnalytics.logWebCheckoutCompleted(source, webPlanLabel(), webVariantLabel())
                _events.trySend(PremiumEvent.ProActive(announce = true))
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private suspend fun currentStorefront(): String? {
        val country = revenueCat.storefrontCountry()
        country?.let { AppAnalytics.setPlayCountry(it) }
        return country
    }

    private fun billingDecision(): BillingDecision = BillingAvailability.decide(revenueCat.billingSignals(storefront))

    private fun logBillingUnavailableOnce(decision: BillingDecision) {
        if (billingUnavailableLogged) return
        billingUnavailableLogged = true
        AppAnalytics.logBillingUnavailable(decision.reason ?: "unknown")
    }

    private fun isSignedIn(): Boolean = FirebaseAuth.getInstance().currentUser != null

    private fun webPlanLabel(): String = _uiState.value.selectedWebPlan?.plan ?: AppAnalytics.Plan.UNKNOWN

    /** Asked again rather than read from [webVariant]: after process death the payment can land before [load] ends. */
    private fun webVariantLabel(): String? = (webVariant ?: revenueCat.webPaywallVariant())?.label

    private fun paywallType(content: PaywallContent): String = when (content) {
        is PaywallContent.Dashboard -> AppAnalytics.PaywallType.DASHBOARD
        PaywallContent.WebCheckout -> AppAnalytics.PaywallType.WEB
        else -> AppAnalytics.PaywallType.CUSTOM
    }

    /** What was on screen when the paywall closed, so closing on the spinner can be counted. */
    fun dismissedPaywallType(): String = when (val content = _uiState.value.content) {
        PaywallContent.Loading -> AppAnalytics.PaywallType.LOADING
        PaywallContent.Error -> AppAnalytics.PaywallType.ERROR
        else -> paywallType(content)
    }

    override fun onCleared() {
        revenueCat.isPremiumUser.removeObserver(premiumObserver)
        super.onCleared()
    }

    private companion object {
        const val KEY_VIEW_LOGGED = "paywall_view_logged"
        const val KEY_WEB_PLAN = "web_plan"
        const val KEY_CONTINUE_AFTER_SIGN_IN = "continue_after_sign_in"
    }
}

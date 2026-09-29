package com.kharagedition.tibetankeyboard.ui.subscription

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.data.local.MonetizationStore
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager.RetentionOffer
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager.RetentionResult
import com.kharagedition.tibetankeyboard.subscription.BillingStore
import com.kharagedition.tibetankeyboard.subscription.CancelReason
import com.kharagedition.tibetankeyboard.subscription.ManageStep
import com.kharagedition.tibetankeyboard.subscription.ManageSubscriptionPolicy
import com.kharagedition.tibetankeyboard.subscription.PlanKind
import com.kharagedition.tibetankeyboard.subscription.SubscriptionSnapshot
import com.kharagedition.tibetankeyboard.subscription.SubscriptionStatus
import com.kharagedition.tibetankeyboard.util.UiText
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The retention offer as the screen shows it. */
data class OfferUi(
    val discountedPrice: String,
    val fullPrice: String,
    val months: Int,
    val percentOff: Int,
)

data class ManageUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val snapshot: SubscriptionSnapshot? = null,
    val step: ManageStep = ManageStep.OVERVIEW,
    val reason: CancelReason? = null,
    val offer: OfferUi? = null,
    /** The offer was shown on this pass through the flow (Back from the hand-off returns to it). */
    val offerShown: Boolean = false,
    /** A restore, offer purchase or offer lookup is running. */
    val busy: Boolean = false,
) {
    val status: SubscriptionStatus? get() = snapshot?.let(SubscriptionStatus::of)
    val canCancel: Boolean get() = snapshot?.let(ManageSubscriptionPolicy::canCancel) == true
}

/** Lambdas the screen can invoke; mirrors the `XxxActions` convention of the other screens. */
class ManageSubscriptionActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onStartCancel: () -> Unit = {},
    val onSelectReason: (CancelReason) -> Unit = {},
    val onContinueFromReason: () -> Unit = {},
    val onAcceptOffer: () -> Unit = {},
    val onDeclineOffer: () -> Unit = {},
    val onKeep: () -> Unit = {},
    /** Continue to Google Play / the billing portal to finish cancelling. */
    val onOpenStore: () -> Unit = {},
    /** A cancelled subscriber wants PRO to continue: the same store page, not a cancellation. */
    val onResubscribe: () -> Unit = {},
    val onRequestRefund: () -> Unit = {},
    val onUpdatePayment: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onContactSupport: () -> Unit = {},
)

/** One-off things for the Activity to do. */
sealed interface ManageEvent {
    data class Message(val text: UiText) : ManageEvent
}

/**
 * State for the manage-subscription flow: overview → reason → (retention offer) → hand-off to
 * the store. Cancelling itself always happens in Google Play (or the card billing portal); the
 * app only explains and links there.
 */
class ManageSubscriptionViewModel(app: Application) : AndroidViewModel(app) {

    private val revenueCat = RevenueCatManager.getInstance()
    private val store = MonetizationStore.getInstance(app)

    private val _uiState = MutableStateFlow(ManageUiState())
    val uiState: StateFlow<ManageUiState> = _uiState.asStateFlow()

    private val _events = Channel<ManageEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var retentionOffer: Deferred<RetentionOffer?>? = null

    /** The offer shown on the offer step, kept for the purchase. */
    private var shownOffer: RetentionOffer? = null

    init {
        load(fresh = false)
    }

    fun load(fresh: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = it.snapshot == null, loadFailed = false) }
            val snapshot = revenueCat.awaitSubscriptionSnapshot(fresh)
            _uiState.update {
                it.copy(
                    loading = false,
                    loadFailed = snapshot == null && it.snapshot == null,
                    snapshot = snapshot ?: it.snapshot,
                )
            }
        }
    }

    /** Back from the user; returns false when the screen should close. */
    fun back(): Boolean {
        val state = _uiState.value
        val previous = ManageSubscriptionPolicy.previous(state.step, state.offerShown) ?: return false
        if (previous == ManageStep.OVERVIEW) AppAnalytics.logCancelAbandoned(state.step.name.lowercase())
        _uiState.update { it.copy(step = previous) }
        return true
    }

    fun startCancel() {
        val snapshot = _uiState.value.snapshot ?: return
        AppAnalytics.logCancelStarted()
        Log.i(TAG, "subs-flow: cancel started — ${snapshot.store} ${snapshot.plan} ${SubscriptionStatus.of(snapshot)}")
        // Look the offer up while the user picks a reason, so Continue doesn't wait on Play.
        if (retentionOffer == null && couldOffer(snapshot)) {
            retentionOffer = viewModelScope.async {
                snapshot.productId?.let { revenueCat.loadRetentionOffer(it, snapshot.basePlanId) }
            }
        }
        _uiState.update { it.copy(step = ManageStep.REASON, reason = null, offerShown = false) }
    }

    fun selectReason(reason: CancelReason) {
        _uiState.update { it.copy(reason = reason) }
    }

    fun continueFromReason() {
        val state = _uiState.value
        val reason = state.reason ?: return
        val snapshot = state.snapshot ?: return
        AppAnalytics.logCancelReason(reason.key)
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val offer = withTimeoutOrNull(OFFER_WAIT_MS) { retentionOffer?.await() }
            val show = ManageSubscriptionPolicy.shouldOfferDiscount(
                reason = reason,
                s = snapshot,
                offerAvailable = offer != null,
                alreadyAccepted = store.retentionOfferAccepted(),
            )
            Log.i(TAG, "subs-flow: cancel reason=${reason.key} offerFound=${offer != null} showOffer=$show")
            if (show) {
                shownOffer = offer
                AppAnalytics.logRetentionOfferShown()
            }
            _uiState.update {
                it.copy(
                    busy = false,
                    step = ManageSubscriptionPolicy.stepAfterReason(show),
                    offerShown = show,
                    offer = if (show && offer != null) offer.toUi() else it.offer,
                )
            }
        }
    }

    /**
     * Take the offer. [purchase] launches Google Play (that part needs the Activity); the flow and
     * its result live here, so a rotation mid-purchase doesn't lose them.
     */
    fun acceptOffer(purchase: suspend (RetentionOffer) -> RetentionResult) {
        val offer = shownOffer ?: return
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            onOfferResult(purchase(offer))
        }
    }

    private fun onOfferResult(result: RetentionResult) {
        when (result) {
            RetentionResult.Accepted -> {
                AppAnalytics.logRetentionOfferAccepted()
                store.markRetentionOfferAccepted()
                val offer = _uiState.value.offer
                if (offer != null) {
                    _events.trySend(
                        ManageEvent.Message(
                            UiText.Plural(R.plurals.sub_offer_accepted, offer.months, listOf(offer.discountedPrice, offer.months))
                        )
                    )
                }
                _uiState.update { it.copy(busy = false, step = ManageStep.OVERVIEW) }
                load(fresh = true)
            }
            RetentionResult.Cancelled -> _uiState.update { it.copy(busy = false) }
            is RetentionResult.Failed -> {
                // Don't leave them on an offer that can't be applied: carry on to the store.
                AppAnalytics.logRetentionOfferFailed(result.code)
                _events.trySend(ManageEvent.Message(UiText.Res(R.string.sub_offer_failed)))
                // Back from the hand-off must not return to an offer that can't be applied.
                _uiState.update { it.copy(busy = false, step = ManageStep.HANDOFF, offerShown = false) }
            }
        }
    }

    fun declineOffer() {
        AppAnalytics.logRetentionOfferDeclined()
        _uiState.update { it.copy(step = ManageStep.HANDOFF) }
    }

    /** The user chose to keep PRO from inside the cancel flow. */
    fun keep() {
        AppAnalytics.logCancelAbandoned(_uiState.value.step.name.lowercase())
        _uiState.update { it.copy(step = ManageStep.OVERVIEW) }
    }

    /** Leaving for the store to finish cancelling. */
    fun onHandoff() {
        AppAnalytics.logCancelHandoff(_uiState.value.reason?.key)
    }

    /** Leaving for the store to turn renewal back on. */
    fun onResubscribe() {
        AppAnalytics.logResubscribeOpened()
    }

    fun restore() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val result = revenueCat.restorePurchases()
            _uiState.update { it.copy(busy = false) }
            val message = when (result) {
                true -> R.string.sub_manage_restored
                false -> R.string.sub_manage_restore_none
                null -> R.string.sub_manage_restore_failed
            }
            _events.trySend(ManageEvent.Message(UiText.Res(message)))
            if (result == true) AppAnalytics.logPurchaseRestored()
            load(fresh = false)
        }
    }

    private fun couldOffer(s: SubscriptionSnapshot) =
        s.store == BillingStore.PLAY && s.plan == PlanKind.MONTHLY && !store.retentionOfferAccepted()

    private fun RetentionOffer.toUi() = OfferUi(discountedPrice, fullPrice, discountedMonths, percentOff)

    private companion object {
        const val TAG = "ManageSubscription"
        const val OFFER_WAIT_MS = 4_000L
    }
}

package com.kharagedition.tibetankeyboard.ui.subscription

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.subscription.BillingStore
import com.kharagedition.tibetankeyboard.subscription.ManageSubscriptionPolicy
import com.kharagedition.tibetankeyboard.subscription.SubscriptionSnapshot
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.util.AppConstant
import com.kharagedition.tibetankeyboard.util.openInAppTab
import com.kharagedition.tibetankeyboard.util.resolve
import com.kharagedition.tibetankeyboard.util.showToast
import kotlinx.coroutines.launch

/**
 * Subscription overview and cancel flow (our own screens; see [ManageSubscriptionViewModel]).
 * Holds only what needs an Activity: store links, the retention-offer purchase and email.
 */
class ManageSubscriptionActivity : AppCompatActivity() {

    private val viewModel: ManageSubscriptionViewModel by viewModels()

    /** Set when the user left for the store, so returning refreshes what they changed there. */
    private var leftForStore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        leftForStore = savedInstanceState?.getBoolean(STATE_LEFT_FOR_STORE) ?: false
        if (savedInstanceState == null) AppAnalytics.logManageSubscriptionOpened()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ManageEvent.Message -> showToast(resolve(event.text))
                    }
                }
            }
        }

        setContent {
            TibetanKeyboardTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                BackHandler { if (!viewModel.back()) finish() }
                ManageSubscriptionScreen(
                    state = state,
                    actions = ManageSubscriptionActions(
                        onBack = { if (!viewModel.back()) finish() },
                        onRetry = { viewModel.load(fresh = true) },
                        onStartCancel = viewModel::startCancel,
                        onSelectReason = viewModel::selectReason,
                        onContinueFromReason = viewModel::continueFromReason,
                        onAcceptOffer = {
                            viewModel.acceptOffer { offer ->
                                RevenueCatManager.getInstance().purchaseRetentionOffer(this@ManageSubscriptionActivity, offer)
                            }
                        },
                        onDeclineOffer = viewModel::declineOffer,
                        onKeep = viewModel::keep,
                        onOpenStore = {
                            viewModel.onHandoff()
                            openManagement(state.snapshot)
                        },
                        onResubscribe = {
                            viewModel.onResubscribe()
                            openManagement(state.snapshot)
                        },
                        onRequestRefund = { openUrl(ManageSubscriptionPolicy.PLAY_ORDER_HISTORY_URL) },
                        onUpdatePayment = { openManagement(state.snapshot) },
                        onRestore = viewModel::restore,
                        onContactSupport = ::emailSupport,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (leftForStore) {
            leftForStore = false
            viewModel.load(fresh = true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LEFT_FOR_STORE, leftForStore)
    }

    /**
     * Google Play's page for this subscription (opened by the Play Store app), or the card billing
     * portal in a browser tab that closes back to this screen.
     */
    private fun openManagement(snapshot: SubscriptionSnapshot?) {
        val url = ManageSubscriptionPolicy.managementUrl(snapshot, packageName)
        if (url == null) {
            // A card subscription without a portal link: support can change or cancel it.
            showToast(getString(R.string.sub_manage_portal_unavailable))
            emailSupport()
            return
        }
        leftForStore = if (snapshot?.store == BillingStore.WEB) openInAppTab(url) else openUrl(url)
    }

    private fun openUrl(url: String): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun emailSupport() {
        val appUserId = RevenueCatManager.getInstance().appUserIdOrNull()
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${AppConstant.SUPPORT_EMAIL}")).apply {
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.premium_title))
            // Lets support find the purchase in RevenueCat without asking for receipts.
            if (appUserId != null) putExtra(Intent.EXTRA_TEXT, getString(R.string.sub_manage_support_body, appUserId))
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showToast(AppConstant.SUPPORT_EMAIL)
        }
    }

    companion object {
        private const val STATE_LEFT_FOR_STORE = "left_for_store"

        fun open(context: Context) {
            val intent = Intent(context, ManageSubscriptionActivity::class.java)
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

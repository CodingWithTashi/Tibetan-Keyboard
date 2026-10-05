package com.kharagedition.tibetankeyboard.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.kharagedition.tibetankeyboard.BuildConfig
import com.kharagedition.tibetankeyboard.ui.subscription.ManageSubscriptionActivity
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.util.openPremiumUpgrade
import com.kharagedition.tibetankeyboard.util.showConfirmationDialog
import com.kharagedition.tibetankeyboard.util.showToast

/** Settings screen — pure framework glue (banner ad View, navigation, confirm dialog). */
class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TibetanKeyboardTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    actions = settingsActions(),
                    // Null for subscribers so the AdView is never built; hiding it in the
                    // composable still called loadAd. Mirrors HomeActivity.observeAdGating().
                    adSlot = if (state.isPremium) null else ({ BannerAd() }),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPremium()
        viewModel.reload()
    }

    private fun settingsActions() = SettingsActions(
        onBack = { finish() },
        onColorChange = viewModel::setColor,
        onStyleChange = viewModel::setStyle,
        onVibrate = viewModel::setVibrate,
        onSound = viewModel::setSound,
        onNotification = viewModel::setNotification,
        onStreakReminder = viewModel::setStreakReminder,
        onUpgrade = { openPremiumUpgrade(AppAnalytics.UpgradeSource.SETTINGS) },
        onManageSubscription = { ManageSubscriptionActivity.open(this) },
        onSignIn = { viewModel.signIn() },
        onLogout = {
            showConfirmationDialog(
                title = getString(R.string.sign_out),
                message = getString(R.string.sign_out_confirm),
                positiveText = getString(R.string.sign_out),
                onPositive = { signOut() },
            )
        },
    )

    private fun signOut() {
        viewModel.signOut {
            showToast(getString(R.string.signed_out))
            AuthManager(this).redirectToHome()
        }
    }

    @Composable
    private fun BannerAd() {
        // Sized to the room it has and centred in it. The fixed 320dp banner was wider than the
        // screen's content on a 320dp phone (its right end was cut off) and sat against the
        // left edge on every wider one.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val widthDp = maxWidth.value.toInt()
            // AdView holds a WebView and timers, so it must be paused/resumed and destroyed.
            val adView = remember(widthDp) {
                AdView(this@SettingsActivity).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                    adUnitId = if (BuildConfig.DEBUG) TEST_BANNER_AD_UNIT else PROD_BANNER_AD_UNIT
                    loadAd(AdRequest.Builder().build())
                }
            }
            DisposableEffect(adView) {
                adView.resume()
                onDispose {
                    adView.pause()
                    adView.destroy()
                }
            }
            key(adView) { AndroidView(factory = { adView }) }
        }
    }

    companion object {
        private const val PROD_BANNER_AD_UNIT = "ca-app-pub-8284901143739274/3790581011"
        private const val TEST_BANNER_AD_UNIT = "ca-app-pub-3940256099942544/6300978111"
    }
}

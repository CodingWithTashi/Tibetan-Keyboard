package com.kharagedition.tibetankeyboard.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.ui.subscription.PremiumActivity
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
                SettingsScreen(state = state, actions = settingsActions(), adSlot = { BannerAd() })
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
        onUpgrade = { startActivity(Intent(this, PremiumActivity::class.java)) },
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
            viewModel.redirectToLogin()
            showToast(getString(R.string.signed_out))
        }
    }

    @Composable
    private fun BannerAd() {
        AndroidView(factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_UNIT
                loadAd(AdRequest.Builder().build())
            }
        })
    }

    companion object {
        private const val BANNER_AD_UNIT = "ca-app-pub-8284901143739274/3790581011"
    }
}

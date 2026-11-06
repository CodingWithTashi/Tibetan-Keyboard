package com.kharagedition.tibetankeyboard.ui.voicetext

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Observer
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.util.openPremiumUpgrade
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvents
import com.kharagedition.tibetankeyboard.util.showToast

/** Voice Text: framework glue only (permission, clipboard, share, settings). */
class VoiceTextActivity : AppCompatActivity() {

    private val viewModel: VoiceTextViewModel by viewModels()

    private val micRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val blocked = !granted &&
            !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
        viewModel.voice.onPermissionResult(granted, blocked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Home only opens this for PRO, but a stale shortcut or a lapsed subscription must not get in:
        // once RevenueCat has answered and says free, send them to the plans.
        val rc = RevenueCatManager.getInstance()
        rc.isPremiumUser.observe(this, Observer { premium ->
            if (!premium && rc.isPremiumKnown && !isFinishing) {
                AppAnalytics.logFeatureGateShown(AppAnalytics.UpgradeSource.HOME_FEATURE_GATE)
                openPremiumUpgrade(AppAnalytics.UpgradeSource.HOME_FEATURE_GATE)
                finish()
            }
        })
        setContent {
            TibetanKeyboardTheme {
                val text by viewModel.text.collectAsState()
                VoiceTextScreen(
                    text = text,
                    voice = viewModel.voice,
                    actions = VoiceTextActions(
                        onBack = { finish() },
                        onTextChange = viewModel::onEdited,
                        onCopy = { copy(text) },
                        onShare = { share(text) },
                        onClear = viewModel::clear,
                        onUndo = viewModel::undo,
                        onAllowMic = { micRequest.launch(Manifest.permission.RECORD_AUDIO) },
                        onOpenSettings = {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                            )
                        },
                    ),
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Back from the system settings with the microphone now allowed.
        if (viewModel.hasMicPermission()) viewModel.voice.onPermissionResult(granted = true, blocked = false)
    }

    override fun onStop() {
        // A recording must not go on unseen; a write-down already under way finishes.
        viewModel.voice.cancelListening(VoiceEvents.EndReason.CANCELLED)
        super.onStop()
    }

    private fun copy(text: String) {
        if (text.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Voice Text", text))
        showToast(getString(R.string.voice_text_copied))
    }

    private fun share(text: String) {
        if (text.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, null))
    }
}

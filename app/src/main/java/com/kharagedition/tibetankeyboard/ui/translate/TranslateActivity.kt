package com.kharagedition.tibetankeyboard.ui.translate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.data.repository.subscriptionCallback
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.util.openPremiumUpgrade
import com.kharagedition.tibetankeyboard.util.showToast

/** AI Translate screen — pure framework glue (auth, clipboard, purchase, navigation). */
class TranslateActivity : AppCompatActivity() {

    private val viewModel: TranslateViewModel by viewModels()
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)

        if (!authManager.isUserAuthenticated()) {
            authManager.redirectToLogin(target = TranslateActivity::class.java)
            return
        }

        authManager.initializeUserSession(subscriptionCallback(
            onError = { android.util.Log.w("TranslateActivity", "RevenueCat init: $it") },
        ))

        AppAnalytics.logTranslateOpened()

        setContent {
            TibetanKeyboardTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                TranslateScreen(state = state, actions = translateActions())
            }
        }
    }

    private fun translateActions() = TranslateActions(
        onBack = { finish() },
        onInputChange = viewModel::setInput,
        onSourceLang = viewModel::setSourceLang,
        onTargetLang = viewModel::setTargetLang,
        onSwap = viewModel::swapLanguages,
        onTranslate = viewModel::translate,
        onModelChange = viewModel::setModel,
        onCopy = { text ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("translation", text))
            showToast(getString(R.string.copied))
        },
        onUpgrade = { openPremiumUpgrade(AppAnalytics.UpgradeSource.TRANSLATE) },
    )

    override fun onResume() {
        super.onResume()
        if (!authManager.isUserAuthenticated()) {
            authManager.redirectToLogin(target = TranslateActivity::class.java)
        } else {
            viewModel.refreshPremium()
        }
    }
}

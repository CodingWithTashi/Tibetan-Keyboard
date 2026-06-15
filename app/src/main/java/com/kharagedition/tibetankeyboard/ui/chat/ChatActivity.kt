package com.kharagedition.tibetankeyboard.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.data.repository.subscriptionCallback
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.util.isValidMessage
import com.kharagedition.tibetankeyboard.util.openPremiumUpgrade
import com.kharagedition.tibetankeyboard.util.showConfirmationDialog
import com.kharagedition.tibetankeyboard.util.showToast

class ChatActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)

        if (!authManager.isUserAuthenticated()) {
            authManager.redirectToLogin()
            return
        }

        // Initialize user session and RevenueCat. Init errors are logged, not shown —
        // they fire transiently while RevenueCat warms up and shouldn't interrupt the user.
        authManager.initializeUserSession(subscriptionCallback(
            onError = { android.util.Log.w("ChatActivity", "RevenueCat init: $it") },
        ))

        viewModel.addWelcomeMessage()
        AppAnalytics.logChatOpened()

        setContent {
            TibetanKeyboardTheme {
                val messages by viewModel.messages.observeAsState(emptyList())
                val isLoading by viewModel.isLoading.observeAsState(false)
                val isPremium by RevenueCatManager.getInstance().isPremiumUser.observeAsState(false)
                val model by viewModel.model.observeAsState(com.kharagedition.tibetankeyboard.ui.settings.SettingsPrefs.DEFAULT_MODEL)

                ChatScreen(
                    messages = messages,
                    isLoading = isLoading,
                    isPremium = isPremium,
                    model = model,
                    actions = chatActions(),
                )
            }
        }
    }

    private fun chatActions() = ChatActions(
        onBack = { finish() },
        onSend = { text ->
            if (text.isValidMessage()) {
                viewModel.sendMessage(text, authManager.getUser()?.uid ?: "")
            } else {
                showToast(getString(R.string.message_invalid))
            }
        },
        onCopy = { text ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
            showToast(getString(R.string.copied))
        },
        onClear = {
            showConfirmationDialog(
                title = getString(R.string.clear_chat),
                message = getString(R.string.clear_chat_confirm),
                positiveText = getString(R.string.clear),
                onPositive = { viewModel.clearMessages(); showToast(getString(R.string.chat_cleared)) },
            )
        },
        onLogout = {
            showConfirmationDialog(
                title = getString(R.string.sign_out),
                message = getString(R.string.sign_out_confirm),
                positiveText = getString(R.string.sign_out),
                onPositive = { signOut() },
            )
        },
        onUpgrade = { openPremiumUpgrade(AppAnalytics.UpgradeSource.CHAT) },
        onModelChange = { viewModel.setModel(it) },
    )

    private fun signOut() {
        authManager.signOut {
            authManager.redirectToLogin()
            showToast(getString(R.string.signed_out))
        }
    }

    override fun onResume() {
        super.onResume()
        if (!authManager.isUserAuthenticated()) {
            authManager.redirectToLogin()
        } else {
            RevenueCatManager.getInstance().refreshCustomerInfo()
        }
    }
}

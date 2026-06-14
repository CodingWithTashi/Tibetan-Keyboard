package com.kharagedition.tibetankeyboard.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.kharagedition.tibetankeyboard.BuildConfig
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.UpdateNotificationManager
import com.kharagedition.tibetankeyboard.ads.NativeTemplateStyle
import com.kharagedition.tibetankeyboard.ads.TemplateView
import com.kharagedition.tibetankeyboard.app.InputMethodActivity
import com.kharagedition.tibetankeyboard.data.repository.subscriptionCallback
import com.kharagedition.tibetankeyboard.ui.about.AboutActivity
import com.kharagedition.tibetankeyboard.ui.chat.ChatActivity
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.ui.settings.SettingsActivity
import com.kharagedition.tibetankeyboard.ui.translate.TranslateActivity
import com.kharagedition.tibetankeyboard.util.AppConstant
import com.kharagedition.tibetankeyboard.util.CommonUtils
import com.kharagedition.tibetankeyboard.util.openPremiumUpgrade
import kotlinx.coroutines.launch

/**
 * Home screen. Holds only framework glue — InputMethodManager queries, native-ad Views,
 * notifications/FCM and navigation. All UI state lives in [HomeViewModel].
 */
class HomeActivity : InputMethodActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private var nativeAd by mutableStateOf<NativeAd?>(null)
    private lateinit var updateManager: UpdateNotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = UpdateNotificationManager(this)

        if (viewModel.isUserAuthenticated()) {
            viewModel.initializeUserSession(subscriptionCallback(
                onSuccess = { Log.d(TAG, "✅ RevenueCat initialized: $it") },
                onError = { Log.e(TAG, "❌ RevenueCat init failed: $it") },
            ))
        }

        setContent {
            TibetanKeyboardTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val ad = nativeAd
                HomeScreen(
                    state = state,
                    actions = homeActions(),
                    adSlot = if (ad != null) {
                        { NativeAdCard(ad) }
                    } else null,
                )
            }
        }

        observeAdGating()
        refreshSetupState()
        requestNotificationPermission()
        subscribeToAllUsersTopic()
        initializeFirebase()
        updateManager.checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        refreshSetupState()
        // CRITICAL: sync purchases on resume to acknowledge pending subscriptions
        // (prevents Google Play auto-cancelling after 3 days).
        if (viewModel.isUserAuthenticated()) {
            viewModel.syncPurchases(subscriptionCallback(
                onError = { Log.w(TAG, "⚠️ Sync failed in onResume: $it") },
            ))
        }
        viewModel.refreshPremium()
    }

    override fun onInputMethodPicked() = refreshSetupState()

    private fun homeActions() = HomeActions(
        onEnableKeyboard = {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        },
        onPickInputMethod = { pickInput() },
        onChat = { openIfPremiumOrUpgrade(ChatActivity::class.java) },
        onTranslate = { openIfPremiumOrUpgrade(TranslateActivity::class.java) },
        onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
        onShare = { shareApp() },
        onRate = { openView(CommonUtils.PLAY_STORE_URL) },
        onAbout = { startActivity(Intent(this, AboutActivity::class.java)) },
        onUpgrade = { openPremiumUpgrade() },
    )

    /**
     * PRO feature entry points. Subscribers get the screen. Signed-in free users get the
     * paywall. Signed-out users can't be classified yet, so we open the screen — it routes
     * them through login and back, after which a free user is gated on the first action.
     */
    private fun openIfPremiumOrUpgrade(target: Class<*>) {
        if (viewModel.uiState.value.isPremium || !viewModel.isUserAuthenticated()) {
            startActivity(Intent(this, target))
        } else {
            openPremiumUpgrade()
        }
    }

    /** Loads/destroys the native ad as premium status changes (ad Views can't live in the VM). */
    private fun observeAdGating() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.isPremium) {
                        nativeAd?.destroy()
                        nativeAd = null
                    } else if (nativeAd == null) {
                        loadNativeAd()
                    }
                }
            }
        }
    }

    /** Reads keyboard-enabled / default-IME status and forwards it to the ViewModel. */
    private fun refreshSetupState() {
        val im = applicationContext.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = im.enabledInputMethodList.toString().contains(PACKAGE)
        val isDefault = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )?.contains(PACKAGE) == true
        viewModel.refreshSetup(enabled, enabled && isDefault)
    }

    @SuppressLint("NewApi")
    private fun loadNativeAd() {
        val adUnitId = if (BuildConfig.DEBUG) AppConstant.TEST_APP_ID else AppConstant.PRODUCTION_ADS_NATIVE
        AdLoader.Builder(this, adUnitId)
            .forNativeAd { ad: NativeAd ->
                if (isDestroyed) { ad.destroy(); return@forNativeAd }
                nativeAd?.destroy()
                nativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "onAdFailedToLoad: ${adError.message}")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    @Composable
    private fun NativeAdCard(ad: NativeAd) {
        AndroidView(
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx).inflate(R.layout.home_native_ad, null) as TemplateView)
                    .apply { setStyles(NativeTemplateStyle.Builder().build()) }
            },
            update = { it.setNativeAd(ad) },
        )
    }

    private fun shareApp() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                putExtra(Intent.EXTRA_TEXT, "\nCheck out this Keyboard Application.\n\n${CommonUtils.PLAY_STORE_URL}")
            }, "choose one"))
        } catch (_: Exception) {
        }
    }

    private fun openView(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    private fun subscribeToAllUsersTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("test-users")
            .addOnCompleteListener { task ->
                Log.d("FCM", if (task.isSuccessful) "Subscribed to all-users topic" else "Failed to subscribe")
            }
    }

    private fun initializeFirebase() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }
            Log.d("FCM", "FCM Registration Token: ${task.result}")
        }
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HomeActivity"
        private const val PACKAGE = "com.kharagedition.tibetankeyboard"
    }
}

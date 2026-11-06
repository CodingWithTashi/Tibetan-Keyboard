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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
import com.kharagedition.tibetankeyboard.InAppUpdateManager
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.ads.NativeTemplateStyle
import com.kharagedition.tibetankeyboard.ads.TemplateView
import com.kharagedition.tibetankeyboard.app.InputMethodActivity
import com.kharagedition.tibetankeyboard.data.repository.subscriptionCallback
import com.kharagedition.tibetankeyboard.service.MyFirebaseMessagingService
import com.kharagedition.tibetankeyboard.ui.about.AboutActivity
import com.kharagedition.tibetankeyboard.ui.chat.ChatActivity
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.ui.journey.JourneyActivity
import com.kharagedition.tibetankeyboard.ui.settings.SettingsActivity
import com.kharagedition.tibetankeyboard.ui.voicetext.VoiceTextActivity
import com.kharagedition.tibetankeyboard.ui.translate.TranslateActivity
import com.kharagedition.tibetankeyboard.util.AppConstant
import com.kharagedition.tibetankeyboard.util.CommonUtils
import com.kharagedition.tibetankeyboard.util.openPremiumUpgrade
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Home screen. Holds only framework glue — InputMethodManager queries, native-ad Views,
 * notifications/FCM and navigation. All UI state lives in [HomeViewModel].
 */
class HomeActivity : InputMethodActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private var nativeAd by mutableStateOf<NativeAd?>(null)
    private var showUpdateBanner by mutableStateOf(false)

    /** Watches for the keyboard being switched on while the user is in the system settings. */
    private var enableWatch: Job? = null

    /**
     * Swallows Back for a moment after Home brought itself back from the system settings. The
     * user may still be pressing Back to leave that screen, and Home is the bottom of its task:
     * the press would close the app just as setup was about to continue.
     */
    private val staleBackGuard = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    private lateinit var inAppUpdateManager: InAppUpdateManager

    // Registered before onCreate — handles both flexible and immediate update flow results.
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Log.w(TAG, "In-app update flow ended with result: ${result.resultCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inAppUpdateManager = InAppUpdateManager(this)
        onBackPressedDispatcher.addCallback(this, staleBackGuard)
        // The process can die while the user is in the system settings; the trip must not.
        if (savedInstanceState?.getBoolean(STATE_ENABLE_TRIP) == true) viewModel.restoreEnableTrip()

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
                    adSlot = if (ad != null) { { NativeAdCard(ad) } } else null,
                    showUpdateBanner = showUpdateBanner,
                    onInstallUpdate = {
                        Log.d(UPDATE_TAG, "onInstallUpdate: user tapped install banner → completing update")
                        showUpdateBanner = false
                        if (BuildConfig.DEBUG) {
                            Log.d(UPDATE_TAG, "onInstallUpdate: DEBUG — no real APK downloaded, skipping completeUpdate(). Production would restart the app here.")
                            android.widget.Toast.makeText(this, "DEBUG: Update flow OK — production would restart & install here", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            inAppUpdateManager.completeFlexibleUpdate()
                        }
                    },
                )
            }
        }

        observeAdGating()
        observeOnboardingPaywall()
        observeSetupPicker()
        refreshSetupState()
        requestNotificationPermission()
        subscribeToAllUsersTopic()
        initializeFirebase()

        val forceImmediate = intent?.getBooleanExtra(MyFirebaseMessagingService.EXTRA_FORCE_UPDATE, false) == true
        triggerUpdateCheck(forceImmediate)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(MyFirebaseMessagingService.EXTRA_FORCE_UPDATE, false)) {
            triggerUpdateCheck(forceImmediate = true)
        }
        // The intent Home sent itself from behind the system settings: it only arrives when the
        // system let Home come back. Read once, so a later re-creation doesn't replay it.
        if (intent.getBooleanExtra(EXTRA_BROUGHT_BACK, false)) {
            intent.removeExtra(EXTRA_BROUGHT_BACK)
            viewModel.onBroughtBack()
            guardAgainstStaleBack()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ENABLE_TRIP, viewModel.enableTripPending)
    }

    override fun onResume() {
        super.onResume()
        // Back in front: whatever happened in the system settings, that trip is over.
        enableWatch?.cancel()
        refreshSetupState(homeInFront = true)
        viewModel.refreshJourney()
        viewModel.refreshAccount()
        // CRITICAL: sync purchases on resume to acknowledge pending subscriptions
        // (prevents Google Play auto-cancelling after 3 days).
        if (viewModel.isUserAuthenticated()) {
            viewModel.syncPurchases(subscriptionCallback(
                onError = { Log.w(TAG, "⚠️ Sync failed in onResume: $it") },
            ))
        }
        viewModel.refreshPremium()
        // Show the install banner if a flexible update finished downloading while we were away.
        Log.d(UPDATE_TAG, "onResume: checking if flexible update already downloaded")
        inAppUpdateManager.checkIfUpdateDownloaded {
            Log.d(UPDATE_TAG, "onResume: update already downloaded → showing install banner")
            showUpdateBanner = true
        }
    }

    override fun onInputMethodPicked() = refreshSetupState()

    private fun triggerUpdateCheck(forceImmediate: Boolean = false) {
        Log.d(UPDATE_TAG, "triggerUpdateCheck: forceImmediate=$forceImmediate")
        inAppUpdateManager.checkForUpdate(
            launcher = updateLauncher,
            forceImmediate = forceImmediate,
            onFlexibleDownloadComplete = {
                Log.d(UPDATE_TAG, "onFlexibleDownloadComplete → showing install banner")
                showUpdateBanner = true
            },
        )
    }

    private fun homeActions() = HomeActions(
        onEnableKeyboard = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.ENABLE_KEYBOARD)
            AppAnalytics.logKeyboardEnableClicked()
            openKeyboardSettings()
        },
        onPickInputMethod = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.PICK_INPUT_METHOD)
            pickInput()
        },
        onChat = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.CHAT)
            openIfPremiumOrUpgrade(ChatActivity::class.java)
        },
        onTranslate = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.TRANSLATE)
            openIfPremiumOrUpgrade(TranslateActivity::class.java)
        },
        onSettings = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.SETTINGS)
            startActivity(Intent(this, SettingsActivity::class.java))
        },
        onShare = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.SHARE)
            shareApp()
        },
        onVoiceText = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.VOICE_TEXT)
            startActivity(Intent(this, VoiceTextActivity::class.java))
        },
        onAbout = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.ABOUT)
            startActivity(Intent(this, AboutActivity::class.java))
        },
        onUpgrade = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.UPGRADE)
            openPremiumUpgrade(AppAnalytics.UpgradeSource.HOME)
        },
        onJourney = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.JOURNEY)
            AppAnalytics.logJourneyOpened(AppAnalytics.JourneySource.HOME)
            startActivity(Intent(this, JourneyActivity::class.java))
        },
        onSignIn = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.SIGN_IN)
            // Home stays underneath, so signing in (or backing out) lands back here.
            AuthManager(this).redirectToLogin(finishCaller = false, returnAfterLogin = true)
        },
        onAccount = {
            AppAnalytics.logHomeAction(AppAnalytics.HomeAction.ACCOUNT)
            // Plan, manage subscription and sign out live in Settings → Account.
            startActivity(Intent(this, SettingsActivity::class.java))
        },
    )

    /**
     * PRO entry points (Chat, Translate). Signed-out users are classifiable now that RevenueCat is
     * configured anonymously; entitled-but-anonymous users sign in here because the server needs a UID.
     */
    private fun openIfPremiumOrUpgrade(target: Class<*>) {
        if (!viewModel.uiState.value.isPremium) {
            AppAnalytics.logFeatureGateShown(AppAnalytics.UpgradeSource.HOME_FEATURE_GATE)
            openPremiumUpgrade(AppAnalytics.UpgradeSource.HOME_FEATURE_GATE)
            return
        }
        if (!viewModel.isUserAuthenticated()) {
            // Link the account first, then return to the screen they asked for.
            AuthManager(this).redirectToLogin(finishCaller = false, target = target)
            return
        }
        startActivity(Intent(this, target))
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

    /**
     * Opens the onboarding paywall when the ViewModel asks (after keyboard setup, on day 3, or
     * once for existing users). Only while Home is RESUMED, so it never pops over a permission
     * dialog. Not an [openPremiumUpgrade]: nobody tapped anything, so it isn't an `upgrade_clicked`.
     */
    private fun observeOnboardingPaywall() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.openPaywall.collect { request ->
                    if (!viewModel.isFresh(request)) return@collect
                    viewModel.onOnboardingPaywallOpened(request)
                    AuthManager(this@HomeActivity).openPremium(request.source)
                }
            }
        }
    }

    /**
     * Setup step 1: the system's keyboard list, where the user switches the keyboard on. Where
     * Home can come back by itself ([EnableTrip.bringsHomeBack]) the list opens inside this task,
     * which is what makes that possible; elsewhere in a task of its own, as it always did. It is
     * opened plainly, with no flag that changes how the system screen itself behaves. If the user
     * leaves it there with the Home button, the next launch clears it (see SplashScreenActivity).
     * The toast is the only guidance we can give over a system screen; it tells the user to come
     * back only where Home will not do so itself.
     */
    private fun openKeyboardSettings() {
        val comesBack = viewModel.bringsHomeBack
        val hint = if (comesBack) R.string.setup_enable_toast_returns else R.string.setup_enable_toast
        Toast.makeText(this, hint, Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            if (!comesBack) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        viewModel.onEnableTripStarted()
        watchEnableTrip()
    }

    /**
     * While the user is in the system settings, notice the keyboard being switched on (the
     * ViewModel logs it there and then) and, where Android allows, bring Home back instead of
     * leaving them to find the way. If the system refuses, nothing happens and they come back as
     * before; the extra tells [onNewIntent] that it did not refuse.
     */
    private fun watchEnableTrip() {
        enableWatch?.cancel()
        enableWatch = lifecycleScope.launch {
            withTimeoutOrNull(ENABLE_WATCH_MS) {
                while (!viewModel.sawKeyboardSwitchedOn(isKeyboardEnabled())) delay(ENABLE_POLL_MS)
            } ?: return@launch
            if (!viewModel.bringsHomeBack) return@launch
            // Stock Android drops a refused start silently; a vendor build that throws instead
            // must not take the app down while it is off screen.
            runCatching {
                startActivity(
                    Intent(this@HomeActivity, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(EXTRA_BROUGHT_BACK, true)
                )
            }.onFailure { Log.w(TAG, "Could not come back from the keyboard settings", it) }
        }
    }

    private fun guardAgainstStaleBack() {
        staleBackGuard.isEnabled = true
        lifecycleScope.launch {
            delay(EnableTrip.STALE_BACK_GUARD_MS)
            staleBackGuard.isEnabled = false
        }
    }

    /**
     * Opens the keyboard picker (step 2) when the ViewModel asks: the user has just come back
     * from switching the keyboard on, so the next tap is saved.
     */
    private fun observeSetupPicker() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.openPicker.collect { trigger ->
                    // The system opens the picker only for the window with input focus, and it
                    // learns of ours a moment after the screen is back. If focus never comes,
                    // give up quietly: step 2 is still on screen to tap.
                    withTimeoutOrNull(PICKER_FOCUS_TIMEOUT_MS) {
                        while (!hasWindowFocus()) delay(PICKER_FOCUS_POLL_MS)
                    } ?: return@collect
                    delay(PICKER_AFTER_FOCUS_MS)
                    val setup = viewModel.uiState.value
                    if (setup.keyboardEnabled && !setup.inputMethodSelected) pickInput(trigger)
                }
            }
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val im = applicationContext.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return im.enabledInputMethodList.toString().contains(PACKAGE)
    }

    /**
     * Reads keyboard-enabled / default-IME status and forwards it to the ViewModel. [homeInFront]
     * is the check made as Home comes to the front; see [HomeViewModel.refreshSetup].
     */
    private fun refreshSetupState(homeInFront: Boolean = false) {
        val enabled = isKeyboardEnabled()
        val isDefault = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )?.contains(PACKAGE) == true
        viewModel.refreshSetup(enabled, enabled && isDefault, homeInFront)
        AppAnalytics.setKeyboardSetup(enabled = enabled, isDefault = enabled && isDefault)
    }

    @SuppressLint("NewApi")
    private fun loadNativeAd() {
        val adUnitId = if (BuildConfig.DEBUG) AppConstant.TEST_ADS_NATIVE else AppConstant.PRODUCTION_ADS_NATIVE
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
            // The ad's own card is a square white block; round it like the cards around it.
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
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
        inAppUpdateManager.unregisterListener()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HomeActivity"
        private const val UPDATE_TAG = "InAppUpdate"
        private const val PACKAGE = "com.kharagedition.tibetankeyboard"

        private const val STATE_ENABLE_TRIP = "enable_trip_pending"
        private const val EXTRA_BROUGHT_BACK = "brought_back_from_keyboard_settings"
        private const val ENABLE_POLL_MS = 300L
        /** Past this, someone still in the system settings comes back on their own. */
        private const val ENABLE_WATCH_MS = 3 * 60_000L
        private const val PICKER_FOCUS_POLL_MS = 50L
        private const val PICKER_FOCUS_TIMEOUT_MS = 2_000L
        private const val PICKER_AFTER_FOCUS_MS = 300L
    }
}

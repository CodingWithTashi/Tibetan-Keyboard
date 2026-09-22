package com.kharagedition.tibetankeyboard.app

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.kharagedition.tibetankeyboard.BuildConfig
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.analytics.UserActivityTracker
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.ui.journey.StreakReminderWorker
import com.kharagedition.tibetankeyboard.util.AppConstant
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TibetanKeyboardApp : Application(), Configuration.Provider {
    lateinit var prefs: SharedPreferences

    /**
     * WorkManager's androidx.startup initializer is removed in the manifest, so the library
     * comes up lazily on the first [androidx.work.WorkManager.getInstance] call — which only
     * ever happens inside [StreakReminderWorker.schedule], where it is guarded. That method's
     * KDoc explains why auto-init had to go.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.WARN)
            .build()

    /**
     * App-lived coroutine scope for fire-and-forget work that must outlive a single screen
     * (e.g. post-login profile sync / analytics that should not be cancelled when LoginActivity
     * finishes). SupervisorJob so one failed child doesn't cancel its siblings.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        // The app is a single premium dark-warm design; force light mode globally so no
        // activity needs to set it individually.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // Product analytics — release-only (no-op in debug builds, see AppAnalytics).
        AppAnalytics.init(this)
        // Per-user activity aggregates (users/{uid}.activity) — drives the "most active users"
        // Firestore query. Throttled to one write/user/day, so resuming any screen is cheap.
        registerActivityLifecycleCallbacks(activityTracker)
        // setup RevenueCat
        setUpRevenueCat()
        val enableEventNotification = prefs.getBoolean("event_notification", true)
        Log.e("TAG", "onCreate: CREATED: $enableEventNotification", )
        if(enableEventNotification){
            FirebaseMessaging.getInstance().subscribeToTopic(AppConstant.TIBETAN_KEYBOARD_APP)
        }else{
            FirebaseMessaging.getInstance().unsubscribeFromTopic(AppConstant.TIBETAN_KEYBOARD_APP)
        }
        // Journey streak reminder — daily on-device check (~7pm). Idempotent (KEEP), and this
        // runs on every process start, so keyboard-only users get it too (the IME shares this
        // Application). The worker honours the Settings toggle and never touches the network.
        // This is also where WorkManager first comes up (auto-init is off), hence last.
        StreakReminderWorker.schedule(this)
    }

    /**
     * Configure RevenueCat at process start, with or without a signed-in user — without this the
     * paywall failed with "Please login first" for everyone who had not signed in.
     */
    private fun setUpRevenueCat() {
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        val manager = RevenueCatManager.getInstance()
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            manager.initialize(this, auth)
        } else {
            manager.configureAnonymous(this)
        }
    }

    /**
     * Records a daily activity ping whenever any app screen comes to the foreground. The tracker
     * itself is throttled (one write/user/day), so a ping on every resume is intentional and cheap.
     */
    private val activityTracker = object : ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            UserActivityTracker.recordActive(activity, UserActivityTracker.Source.APP)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
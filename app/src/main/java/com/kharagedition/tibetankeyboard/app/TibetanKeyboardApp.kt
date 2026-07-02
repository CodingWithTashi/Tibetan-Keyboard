package com.kharagedition.tibetankeyboard.app

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.firebase.messaging.FirebaseMessaging
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.analytics.UserActivityTracker
import com.kharagedition.tibetankeyboard.ui.journey.StreakReminderWorker
import com.kharagedition.tibetankeyboard.util.AppConstant
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesAreCompletedBy.MY_APP
import com.revenuecat.purchases.PurchasesAreCompletedBy.REVENUECAT
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TibetanKeyboardApp : Application() {
    lateinit var prefs: SharedPreferences

    /**
     * App-lived coroutine scope for fire-and-forget work that must outlive a single screen
     * (e.g. post-login profile sync / analytics that should not be cancelled when LoginActivity
     * finishes). SupervisorJob so one failed child doesn't cancel its siblings.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
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
        StreakReminderWorker.schedule(this)

        super.onCreate()
    }

    private fun setUpRevenueCat() {
        // DO NOT initialize RevenueCat here without a user ID
        // RevenueCat will be configured in RevenueCatManager when user is authenticated
        // This ensures purchases are always tied to the Firebase user ID
        Purchases.logLevel = LogLevel.DEBUG
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
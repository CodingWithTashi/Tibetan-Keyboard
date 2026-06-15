package com.kharagedition.tibetankeyboard.app

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.firebase.messaging.FirebaseMessaging
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
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
        // setup RevenueCat
        setUpRevenueCat()
        val enableEventNotification = prefs.getBoolean("event_notification", true)
        Log.e("TAG", "onCreate: CREATED: $enableEventNotification", )
        if(enableEventNotification){
            FirebaseMessaging.getInstance().subscribeToTopic(AppConstant.TIBETAN_KEYBOARD_APP)
        }else{
            FirebaseMessaging.getInstance().unsubscribeFromTopic(AppConstant.TIBETAN_KEYBOARD_APP)
        }

        super.onCreate()
    }

    private fun setUpRevenueCat() {
        // DO NOT initialize RevenueCat here without a user ID
        // RevenueCat will be configured in RevenueCatManager when user is authenticated
        // This ensures purchases are always tied to the Firebase user ID
        Purchases.logLevel = LogLevel.DEBUG
    }
}
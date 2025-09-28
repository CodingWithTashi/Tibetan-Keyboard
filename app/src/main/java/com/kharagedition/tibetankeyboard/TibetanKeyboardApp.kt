package com.kharagedition.tibetankeyboard

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.firebase.messaging.FirebaseMessaging
import com.kharagedition.tibetankeyboard.util.AppConstant
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesAreCompletedBy.MY_APP
import com.revenuecat.purchases.PurchasesAreCompletedBy.REVENUECAT
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener

class TibetanKeyboardApp : Application() {
    lateinit var prefs: SharedPreferences
    override fun onCreate() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
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
        Purchases.logLevel = LogLevel.DEBUG
        val apiKey = if (BuildConfig.DEBUG) {
            "goog_HqifnUJxdgpKcyrUFhRfJfAYIap"
        } else {
            "goog_HqifnUJxdgpKcyrUFhRfJfAYIap"
        }
        Purchases.configure(
            PurchasesConfiguration.Builder(this, apiKey)
                //.appUserID(null)
                .purchasesAreCompletedBy(REVENUECAT)
                .build()

        )
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { customerInfo -> Log.d("TAG", "onCreate: Customer Info Updated: ${customerInfo.toString()}") }

    }
}
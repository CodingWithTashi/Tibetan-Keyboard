package com.kharagedition.tibetankeyboard

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.firebase.messaging.FirebaseMessaging
import com.kharagedition.tibetankeyboard.util.AppConstant

class TibetanKeyboardApp : Application() {
    lateinit var prefs: SharedPreferences
    override fun onCreate() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val enableEventNotification = prefs.getBoolean("event_notification", true)
        Log.e("TAG", "onCreate: CREATED: $enableEventNotification", )
        if(enableEventNotification){
            FirebaseMessaging.getInstance().subscribeToTopic(AppConstant.TIBETAN_KEYBOARD_APP)
        }else{
            FirebaseMessaging.getInstance().unsubscribeFromTopic(AppConstant.TIBETAN_KEYBOARD_APP)
        }

        super.onCreate()
    }
}
package com.kharagedition.tibetankeyboard.util

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window

/**
 * Created by kharag on 31,July,2021
 */
class CommonUtils {
    companion object {
        var INSTAGRAM_URL = "https://www.instagram.com/kontashi35/"
        var FACEBOOK_URL = "https://www.facebook.com/kharagedition"
        var GITHUB_URL = "https://github.com/CodingWithTashi/Tibetan-Keyboard"
        var PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.kharagedition.tibetankeyboard"
        var PLAY_STORE_DICTIONARY_URL = "https://play.google.com/store/apps/details?id=com.kharagedition.tibetandictionary"
        var PLAY_STORE_CALENDAR_URL = "https://play.google.com/store/apps/details?id=com.codingwithtashi.tibetan_calender"

        fun hideStatusBar(window: Window) {

            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT || Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT_WATCH) {
                window.attributes.flags =
                    window.attributes.flags
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                val uiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                window.decorView.systemUiVisibility = uiVisibility
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.attributes.flags =
                    window.attributes.flags
                window.statusBarColor = Color.parseColor("#00ff0000")
                window.navigationBarColor = Color.parseColor("#00ff0000")
            }
        }
    }


}
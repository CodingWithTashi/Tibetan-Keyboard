package com.kharagedition.tibetankeyboard

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "userPreferences"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_USER_ID = "userId"
        private const val KEY_USER_NAME = "username"
        private const val KEY_USER_EMAIL = "userEmail"
        private const val KEY_USER_PHOTO_URL = "userPhotoUrl"
        private const val KEY_FIRST_TIME_USER = "firstTimeUser"
        private const val KEY_PREMIUM_USER = "premiumUser"
        private const val KEY_LAST_ACTIVE = "lastActive"
    }

    fun saveUserLoginState(
        isLoggedIn: Boolean,
        userId: String = "",
        userName: String = "",
        userEmail: String = "",
        userPhotoUrl: String = ""
    ) {
        sharedPreferences.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, userName)
            putString(KEY_USER_EMAIL, userEmail)
            putString(KEY_USER_PHOTO_URL, userPhotoUrl)
            if (isLoggedIn && isFirstTimeUser()) {
                putBoolean(KEY_FIRST_TIME_USER, false)
            }
            apply()
        }
    }

    fun isUserLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getUserId(): String {
        return sharedPreferences.getString(KEY_USER_ID, "") ?: ""
    }

    fun getUserName(): String {
        return sharedPreferences.getString(KEY_USER_NAME, "") ?: ""
    }

    fun getUserEmail(): String {
        return sharedPreferences.getString(KEY_USER_EMAIL, "") ?: ""
    }

    fun getUserPhotoUrl(): String {
        return sharedPreferences.getString(KEY_USER_PHOTO_URL, "") ?: ""
    }

    fun isFirstTimeUser(): Boolean {
        return sharedPreferences.getBoolean(KEY_FIRST_TIME_USER, true)
    }

    fun clearUserData() {
        sharedPreferences.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            putString(KEY_USER_ID, "")
            putString(KEY_USER_NAME, "")
            putString(KEY_USER_EMAIL, "")
            putString(KEY_USER_PHOTO_URL, "")
            apply()
        }
    }

    fun setPremiumUser(premium: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_PREMIUM_USER, premium).apply()
        if (premium) {
            updateLastActive()
        }
    }
    fun updateLastActive() {
        sharedPreferences.edit().putLong(KEY_LAST_ACTIVE, System.currentTimeMillis()).apply()
    }
}
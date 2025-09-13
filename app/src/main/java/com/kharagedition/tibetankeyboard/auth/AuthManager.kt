package com.kharagedition.tibetankeyboard.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kharagedition.tibetankeyboard.LoginActivity
import com.kharagedition.tibetankeyboard.UserPreferences
import com.kharagedition.tibetankeyboard.subscription.RevenueCatManager

/**
 * Manages authentication state and user session
 */
class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = Firebase.auth
    private val userPreferences: UserPreferences = UserPreferences(context)
    private val revenueCatManager = RevenueCatManager.getInstance()

    /**
     * Check if user is authenticated
     */
    fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null && userPreferences.isUserLoggedIn()
    }

    /**
     * Get current user display name
     */
    fun getCurrentUserName(): String {
        return userPreferences.getUserName().takeIf { it.isNotEmpty() } ?: "User"
    }

    /**
     * Get current user photo URL
     */
    fun getCurrentUserPhotoUrl(): String {
        return userPreferences.getUserPhotoUrl()
    }

    /**
     * Sign out user from all services
     */
    fun signOut(onComplete: () -> Unit) {

        revenueCatManager.logout(object : RevenueCatManager.SubscriptionCallback {
            override fun onSuccess(message: String) {
                auth.signOut()
                userPreferences.clearUserData()
                onComplete()
            }

            override fun onError(error: String) {
                auth.signOut()
                userPreferences.clearUserData()
                onComplete()
            }

            override fun onUserCancelled() {

            }
        })
    }

    /**
     * Redirect to login activity
     */
    fun redirectToLogin() {


        val intent = Intent(context, LoginActivity::class.java)

        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)

        (context as? Activity)?.finish()
    }

    /**
     * Initialize user session (call this in activities)
     */
    fun initializeUserSession(callback: RevenueCatManager.SubscriptionCallback? = null) {
        if (!isUserAuthenticated()) {
            redirectToLogin()
            return
        }

        // Initialize RevenueCat with current user
        revenueCatManager.initialize(auth, callback)
    }

    /**
     * Check if this is user's first time
     */
    fun isFirstTimeUser(): Boolean {
        return userPreferences.isFirstTimeUser()
    }
    fun getUser(): FirebaseUser? {
        return auth.currentUser
    }
}
package com.kharagedition.tibetankeyboard.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.ui.login.LoginActivity
import com.kharagedition.tibetankeyboard.ui.subscription.PremiumActivity
import com.kharagedition.tibetankeyboard.data.local.UserPreferences
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager

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
                finishSignOut()
                onComplete()
            }

            override fun onError(error: String) {
                finishSignOut()
                onComplete()
            }

            override fun onUserCancelled() {

            }
        })
    }

    /**
     * Tear down every cached session. Firebase's signOut alone leaves Google's last-used account
     * cached on the device, so the next sign-in silently re-picks it; signing out of the
     * GoogleSignInClient too forces the account chooser to reappear on the next login.
     */
    private fun finishSignOut() {
        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso).signOut()
        userPreferences.clearUserData()
        AppAnalytics.logLogout()
        AppAnalytics.setUser(null)
    }

    /**
     * Redirect to login activity
     */
    fun redirectToLogin(
        openPremiumAfter: Boolean = false,
        finishCaller: Boolean = true,
        target: Class<*>? = null,
    ) {
        val intent = Intent(context, LoginActivity::class.java)

        if (openPremiumAfter) {
            intent.putExtra(LoginActivity.EXTRA_OPEN_PREMIUM_AFTER_LOGIN, true)
        }
        if (target != null) {
            intent.putExtra(LoginActivity.EXTRA_POST_LOGIN_TARGET, target.name)
        }

        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)

        // Keep the caller alive when asked (e.g. Home stays beneath the paywall so
        // closing the paywall never leaves an empty back stack).
        if (finishCaller) (context as? Activity)?.finish()
    }

    /**
     * Open the premium paywall directly (used for already-signed-in free users tapping a
     * PRO feature). Safe to call from a non-Activity context such as the IME service.
     */
    fun openPremium() {
        val intent = Intent(context, PremiumActivity::class.java)

        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    /**
     * Initialize user session (call this in activities)
     */
    fun initializeUserSession(callback: RevenueCatManager.SubscriptionCallback? = null) {
        if (!isUserAuthenticated()) {
            redirectToLogin()
            return
        }

        // Initialize RevenueCat with current user and Firebase UID
        revenueCatManager.initialize(context, auth, callback)
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
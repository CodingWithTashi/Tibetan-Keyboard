package com.kharagedition.tibetankeyboard.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.auth.UnlockDestination
import com.kharagedition.tibetankeyboard.auth.UnlockRouter

/**
 * Extension functions for common UI operations
 */

/**
 * THE single entry point for every "unlock PRO / upgrade" action across the app and IME.
 * Never show a custom paywall dialog — route here so users always land on the same
 * "Unlock everything" subscription page (signing in first if needed).
 */
fun Context.openPremiumUpgrade() {
    val authManager = AuthManager(this)
    when (UnlockRouter.destinationFor(authManager.isUserAuthenticated())) {
        UnlockDestination.PREMIUM -> authManager.openPremium()
        // Keep the caller (Home/Settings) beneath so closing the paywall after login
        // can never leave an empty back stack and close the app.
        UnlockDestination.LOGIN_THEN_PREMIUM ->
            authManager.redirectToLogin(openPremiumAfter = true, finishCaller = false)
    }
}

/**
 * Setup edge-to-edge display with proper window insets handling
 */
fun Activity.setupEdgeToEdgeWithKeyboard(rootView: View) {
    // Set window soft input mode to resize
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

        val bottomPadding = if (imeInsets.bottom > systemBars.bottom) imeInsets.bottom else systemBars.bottom
        v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding)
        insets
    }
}

/**
 * Load user profile image with Glide
 */
fun ImageView.loadProfileImage(imageUrl: String?) {
    if (!imageUrl.isNullOrEmpty()) {
        Glide.with(context)
            .load(imageUrl)
            .placeholder(R.drawable.ic_default_avatar)
            .error(R.drawable.ic_default_avatar)
            .into(this)
    } else {
        setImageResource(R.drawable.ic_default_avatar)
    }
}

/**
 * Show a confirmation dialog
 */
fun Context.showConfirmationDialog(
    title: String,
    message: String,
    positiveText: String = "OK",
    negativeText: String = "Cancel",
    onPositive: () -> Unit,
    onNegative: (() -> Unit)? = null
) {
    com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveText) { _, _ -> onPositive() }
        .setNegativeButton(negativeText) { _, _ -> onNegative?.invoke() }
        .show()
}

/**
 * Show welcome snackbar
 */
fun View.showWelcomeSnackbar(userName: String) {
    val welcomeMessage = if (userName.isNotEmpty()) {
        "Welcome to the chat, $userName! 🎉"
    } else {
        "Welcome to the chat! 🎉"
    }

    Snackbar.make(this, welcomeMessage, Snackbar.LENGTH_LONG)
        .setAction("Got it!") { }
        .show()
}

/**
 * Show toast message
 */
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * Show long toast message
 */
fun Context.showLongToast(message: String) {
    showToast(message, Toast.LENGTH_LONG)
}

/**
 * Validate message length
 */
fun String.isValidMessage(maxLength: Int = 300): Boolean {
    return isNotBlank() && length <= maxLength
}
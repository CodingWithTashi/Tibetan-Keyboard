package com.kharagedition.tibetankeyboard.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val authManager = AuthManager(app)

    val isPremiumUser: LiveData<Boolean> = RevenueCatManager.getInstance().isPremiumUser

    fun isUserAuthenticated(): Boolean = authManager.isUserAuthenticated()

    fun signOut(onComplete: () -> Unit) {
        authManager.signOut(onComplete)
    }

    fun redirectToLogin() {
        authManager.redirectToLogin()
    }

    fun refreshCustomerInfo() {
        RevenueCatManager.getInstance().refreshCustomerInfo()
    }
}

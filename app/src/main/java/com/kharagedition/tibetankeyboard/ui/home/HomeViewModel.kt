package com.kharagedition.tibetankeyboard.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val authManager = AuthManager(app)

    val isPremiumUser: LiveData<Boolean> = RevenueCatManager.getInstance().isPremiumUser

    fun isUserAuthenticated(): Boolean = authManager.isUserAuthenticated()

    fun initializeUserSession(callback: RevenueCatManager.SubscriptionCallback) {
        authManager.initializeUserSession(callback)
    }

    fun syncPurchases(callback: RevenueCatManager.SubscriptionCallback) {
        RevenueCatManager.getInstance().syncPurchases(callback)
    }

    fun refreshCustomerInfo() {
        RevenueCatManager.getInstance().refreshCustomerInfo()
    }
}

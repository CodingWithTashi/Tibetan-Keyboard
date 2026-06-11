package com.kharagedition.tibetankeyboard.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import com.kharagedition.tibetankeyboard.auth.AuthManager
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the Settings UI state. Reads/writes the keyboard preferences (the same store the IME
 * reads) and exposes premium status + auth actions, keeping the Activity free of logic.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val authManager = AuthManager(app)
    private val premiumLiveData = RevenueCatManager.getInstance().isPremiumUser

    private val _uiState = MutableStateFlow(
        SettingsPrefs.read(app).copy(isAuthenticated = authManager.isUserAuthenticated())
    )
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    private val premiumObserver = Observer<Boolean> { isPremium ->
        _uiState.update { it.copy(isPremium = isPremium) }
    }

    init {
        premiumLiveData.observeForever(premiumObserver)
        RevenueCatManager.getInstance().refreshCustomerInfo()
    }

    fun setColor(value: String) = write(SettingsPrefs.KEY_COLOR, value) { it.copy(color = value) }
    fun setStyle(value: String) = write(SettingsPrefs.KEY_STYLE, value) { it.copy(style = value) }
    fun setVibrate(on: Boolean) = write(SettingsPrefs.KEY_VIBRATE, on) { it.copy(vibrate = on) }
    fun setSound(on: Boolean) = write(SettingsPrefs.KEY_SOUND, on) { it.copy(sound = on) }
    fun setNotification(on: Boolean) = write(SettingsPrefs.KEY_NOTIFICATION, on) { it.copy(eventNotification = on) }

    private fun write(key: String, value: String, reduce: (SettingsState) -> SettingsState) {
        SettingsPrefs.putString(getApplication(), key, value)
        _uiState.update(reduce)
    }

    private fun write(key: String, value: Boolean, reduce: (SettingsState) -> SettingsState) {
        SettingsPrefs.putBoolean(getApplication(), key, value)
        _uiState.update(reduce)
    }

    /** Reload prefs in case they changed elsewhere (e.g. legacy preference screen). */
    fun reload() {
        val current = _uiState.value
        _uiState.value = SettingsPrefs.read(getApplication())
            .copy(isPremium = current.isPremium, isAuthenticated = authManager.isUserAuthenticated())
    }

    fun signOut(onComplete: () -> Unit) = authManager.signOut(onComplete)
    fun redirectToLogin() = authManager.redirectToLogin()
    fun refreshPremium() = RevenueCatManager.getInstance().refreshCustomerInfo()

    override fun onCleared() {
        premiumLiveData.removeObserver(premiumObserver)
    }
}

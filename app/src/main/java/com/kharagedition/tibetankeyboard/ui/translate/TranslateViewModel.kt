package com.kharagedition.tibetankeyboard.ui.translate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetankeyboard.data.repository.AIService
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager
import com.kharagedition.tibetankeyboard.ui.settings.SettingsPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable UI state for the AI Translate screen. */
data class TranslateUiState(
    val sourceLang: String = "bo",
    val targetLang: String = "en",
    val input: String = "",
    val output: String = "",
    val isLoading: Boolean = false,
    val model: String = SettingsPrefs.DEFAULT_MODEL,
    val isPremium: Boolean = false,
    val error: String? = null,
)

/**
 * Owns the AI Translate state. Translation runs through [AIService] (Claude
 * backend) using the user-selected model; premium status is observed from
 * RevenueCat.
 */
class TranslateViewModel(app: Application) : AndroidViewModel(app) {

    private val aiService = AIService()
    private val premiumLiveData = RevenueCatManager.getInstance().isPremiumUser

    private val _uiState = MutableStateFlow(
        TranslateUiState(model = SettingsPrefs.readModel(app))
    )
    val uiState: StateFlow<TranslateUiState> = _uiState.asStateFlow()

    private val premiumObserver = Observer<Boolean> { isPremium ->
        _uiState.update { it.copy(isPremium = isPremium) }
    }

    init {
        premiumLiveData.observeForever(premiumObserver)
        RevenueCatManager.getInstance().refreshCustomerInfo()
    }

    fun setInput(value: String) = _uiState.update { it.copy(input = value) }

    fun setSourceLang(value: String) = _uiState.update { it.copy(sourceLang = value) }

    fun setTargetLang(value: String) = _uiState.update { it.copy(targetLang = value) }

    fun swapLanguages() = _uiState.update {
        it.copy(sourceLang = it.targetLang, targetLang = it.sourceLang, input = it.output, output = it.input)
    }

    fun setModel(value: String) {
        SettingsPrefs.putString(getApplication(), SettingsPrefs.KEY_AI_MODEL, value)
        _uiState.update { it.copy(model = value) }
    }

    fun refreshPremium() = RevenueCatManager.getInstance().refreshCustomerInfo()

    fun translate() {
        val state = _uiState.value
        if (state.input.isBlank() || state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null, output = "") }
        viewModelScope.launch {
            val result = aiService.translateText(
                text = state.input.trim(),
                sourceLang = state.sourceLang,
                targetLang = state.targetLang,
                model = state.model,
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    output = if (result.error == null) result.translatedText else "",
                    error = result.error,
                )
            }
        }
    }

    override fun onCleared() {
        premiumLiveData.removeObserver(premiumObserver)
    }
}

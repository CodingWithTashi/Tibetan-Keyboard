package com.kharagedition.tibetankeyboard.ui.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the Login screen's loading state. The Google/Firebase sign-in flow itself stays in
 * the Activity because it depends on ActivityResult launchers and Activity-scoped callbacks.
 */
class LoginViewModel : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
}

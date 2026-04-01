package com.kharagedition.tibetankeyboard.ui.transliteration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetankeyboard.data.repository.AIRepository
import com.kharagedition.tibetankeyboard.data.repository.TransliterationResult
import kotlinx.coroutines.launch

class TransliterationViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AIRepository(app)

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _result = MutableLiveData<TransliterationResult?>()
    val result: LiveData<TransliterationResult?> = _result

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun transliterate(text: String, sourceSystem: String, targetSystem: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val transliterationResult = repository.transliterate(text, sourceSystem, targetSystem, userId)
                _result.value = transliterationResult
            } catch (e: Exception) {
                _error.value = "Transliteration failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

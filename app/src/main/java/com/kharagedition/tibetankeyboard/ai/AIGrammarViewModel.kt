package com.kharagedition.tibetankeyboard.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.IOException

data class GrammarCorrection(
    val id: String,
    val originalText: String,
    val correctedText: String,
    val reason: String,
    val confidence: Double,
    val alternatives: List<String>,
    val explanation: String
)

data class GrammarAnalysisResult(
    val corrections: List<GrammarCorrection>,
    val overallScore: Double,
    val estimatedReadingLevel: String,
    val tone: String
)

/**
 * ViewModel for Grammar Analysis
 */
class AIGrammarViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AIRepository(app)

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _grammarResult = MutableLiveData<GrammarAnalysisResult?>()
    val grammarResult: LiveData<GrammarAnalysisResult?> = _grammarResult

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun analyzeGrammar(text: String, userId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val result = repository.analyzeGrammar(text, userId)
                _grammarResult.value = result

            } catch (e: IOException) {
                _error.value = "Network error. Please check your connection."
            } catch (e: Exception) {
                _error.value = "Error analyzing grammar: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getToneSuggestions(text: String, userId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val result = repository.getToneSuggestions(text, userId)
                // Handle tone suggestions

            } catch (e: Exception) {
                _error.value = "Error getting suggestions: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearResults() {
        _grammarResult.value = null
        _error.value = null
    }
}

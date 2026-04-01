package com.kharagedition.tibetankeyboard.ai

import android.app.Application
import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject

/**
 * Repository for AI feature API calls
 */
class AIRepository(private val app: Application) {

    private val context: Context = app.applicationContext
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    // Backend API URL (update with your Firebase function URL)
    private val baseUrl = "https://asia-south1-tibetan-keyboard-123456.cloudfunctions.net"

    /**
     * Analyze Tibetan grammar using AI
     */
    suspend fun analyzeGrammar(text: String, userId: String): GrammarAnalysisResult {
        return withContext(Dispatchers.IO) {
            val endpoint = "$baseUrl/api/grammar/analyze"

            val requestBody = JSONObject().apply {
                put("text", text)
                put("mode", "detailed")
                put("style", "formal")
                put("contextualInfo", JSONObject().apply {
                    put("userLevel", "intermediate")
                    put("documentType", "casual")
                })
            }

            val response = makePostRequest(endpoint, requestBody.toString(), userId)
            parseGrammarResponse(response)
        }
    }

    /**
     * Get tone suggestions for text
     */
    suspend fun getToneSuggestions(text: String, userId: String): List<String> {
        return withContext(Dispatchers.IO) {
            val endpoint = "$baseUrl/api/grammar/suggestions"

            val requestBody = JSONObject().apply {
                put("text", text)
                put("type", "alternatives")
            }

            makePostRequest(endpoint, requestBody.toString(), userId)
            listOf("Formal", "Casual", "Poetic")
        }
    }

    /**
     * Convert text using transliteration
     */
    suspend fun transliterate(
        text: String,
        sourceSystem: String,
        targetSystem: String,
        userId: String
    ): TransliterationResult {
        return withContext(Dispatchers.IO) {
            val endpoint = "$baseUrl/api/transliterate/convert"

            val requestBody = JSONObject().apply {
                put("text", text)
                put("sourceSystem", sourceSystem)
                put("targetSystem", targetSystem)
            }

            val response = makePostRequest(endpoint, requestBody.toString(), userId)
            parseTransliterationResponse(response)
        }
    }

    /**
     * Make POST request to API
     */
    private fun makePostRequest(endpoint: String, body: String, userId: String): String {
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("userid", userId)
            connection.setRequestProperty("User-Agent", "TibetanKeyboard/2.1.12")
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            // Send request body
            connection.outputStream.use { output ->
                output.write(body.toByteArray())
            }

            // Read response
            return if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "{\"error\":\"Unknown error\"}"
            }

        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parse grammar analysis response
     */
    private fun parseGrammarResponse(jsonResponse: String): GrammarAnalysisResult {
        return try {
            val json = JSONObject(jsonResponse)
            val data = json.optJSONObject("data") ?: JSONObject()

            val corrections = mutableListOf<GrammarCorrection>()
            val correctionArray = data.optJSONArray("corrections") ?: return GrammarAnalysisResult(
                corrections = emptyList(),
                overallScore = 0.0,
                estimatedReadingLevel = "unknown",
                tone = "neutral"
            )

            for (i in 0 until correctionArray.length()) {
                val corrObj = correctionArray.getJSONObject(i)
                corrections.add(
                    GrammarCorrection(
                        id = corrObj.optString("id", ""),
                        originalText = corrObj.optString("originalText", ""),
                        correctedText = corrObj.optString("correctedText", ""),
                        reason = corrObj.optString("reason", ""),
                        confidence = corrObj.optDouble("confidence", 0.0),
                        alternatives = mutableListOf<String>().apply {
                            val altArray = corrObj.optJSONArray("alternatives")
                            for (j in 0 until (altArray?.length() ?: 0)) {
                                add(altArray?.getString(j) ?: "")
                            }
                        },
                        explanation = corrObj.optString("explanation", "")
                    )
                )
            }

            GrammarAnalysisResult(
                corrections = corrections,
                overallScore = data.optDouble("overallScore", 0.0),
                estimatedReadingLevel = data.optString("estimatedReadingLevel", "intermediate"),
                tone = data.optJSONObject("toneAnalysis")?.optString("detectedTone", "neutral") ?: "neutral"
            )

        } catch (e: Exception) {
            GrammarAnalysisResult(
                corrections = emptyList(),
                overallScore = 0.0,
                estimatedReadingLevel = "unknown",
                tone = "neutral"
            )
        }
    }

    /**
     * Parse transliteration response
     */
    private fun parseTransliterationResponse(jsonResponse: String): TransliterationResult {
        return try {
            val json = JSONObject(jsonResponse)
            val data = json.optJSONObject("data") ?: JSONObject()

            TransliterationResult(
                result = data.optString("result", ""),
                alternatives = emptyList(),
                confidence = data.optDouble("confidence", 0.0),
                pronunciation = data.optString("pronunciation", "")
            )

        } catch (e: Exception) {
            TransliterationResult("", emptyList(), 0.0, "")
        }
    }
}

data class TransliterationResult(
    val result: String,
    val alternatives: List<String>,
    val confidence: Double,
    val pronunciation: String = ""
)

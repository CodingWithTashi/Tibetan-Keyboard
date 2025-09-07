package com.kharagedition.tibetankeyboard.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AIService {

    data class GrammarResult(
        val correctedText: String,
        val corrections: List<String>
    )

    data class RephraseResult(
        val rephrasedText: String,
        val style: String,
        val improvements: List<String>
    )

    data class TranslationResult(
        val translatedText: String,
        val sourceLanguage: String,
        val targetLanguage: String,
        val confidence: Double
    )

    // Translation API Request/Response data classes
    @Serializable
    data class TranslationRequest(
        val text: String,
        @SerialName("source_lang")
        val sourceLang: String,
        @SerialName("target_lang")
        val targetLang: String
    )

    @Serializable
    data class TranslationResponse(
        @SerialName("translated_text")
        val translatedText: String,
        @SerialName("source_language")
        val sourceLanguage: String,
        @SerialName("target_language")
        val targetLanguage: String,
        val confidence: Double
    )

    // API Request/Response data classes
    @Serializable
    data class GrammarCheckRequest(
        val text: String,
        val model: String = "openai",
        val language: String = "auto",
        @SerialName("max_chunk_size")
        val maxChunkSize: Int = 500
    )

    @Serializable
    data class Correction(
        @SerialName("word_index")
        val wordIndex: Int,
        @SerialName("incorrect_word")
        val incorrectWord: String,
        val suggestion: List<String>,
        val reason: String
    )

    @Serializable
    data class CorrectedText(
        @SerialName("word_counting")
        val wordCounting: List<String>,
        val corrections: List<Correction>,
        @SerialName("corrected_statement")
        val correctedStatement: String
    )

    @Serializable
    data class GrammarCheckResponse(
        @SerialName("detected_language")
        val detectedLanguage: String,
        @SerialName("original_text")
        val originalText: String,
        @SerialName("corrected_text")
        val correctedText: CorrectedText,
        @SerialName("chunks_count")
        val chunksCount: Int,
        @SerialName("model_used")
        val modelUsed: String,
        @SerialName("processing_time_ms")
        val processingTimeMs: Double
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    suspend fun checkGrammar(text: String): GrammarResult {
        return withContext(Dispatchers.IO) {
            try {
                if (text.isEmpty()) return@withContext GrammarResult("", emptyList())

                val response = RetrofitClient.api.checkGrammar(GrammarCheckRequest(text = text))

                val corrections = response.correctedText.corrections.map {
                    "${it.incorrectWord} → ${it.suggestion.firstOrNull() ?: ""} (${it.reason})"
                }

                GrammarResult(response.correctedText.correctedStatement, corrections)

            } catch (e: Exception) {
                println("Error: ${e.message}")
                e.printStackTrace()
                GrammarResult(text, listOf("Error occurred during grammar check: ${e.message}"))
            }
        }
    }

    suspend fun translateText(text: String, sourceLang: String, targetLang: String): TranslationResult {
        return withContext(Dispatchers.IO) {
            try {
                if (text.isEmpty()) {
                    return@withContext TranslationResult("", sourceLang, targetLang, 0.0)
                }

                val response = RetrofitClient.api.translateText(TranslationRequest(text = text, sourceLang = sourceLang, targetLang = targetLang))


                // Simulate API delay for now
                delay(1500)

                // Mock translation logic - replace with actual API call later
                val translatedText = when {
                    sourceLang == "bo" && targetLang == "en" -> {
                        // Tibetan to English mock translation
                        mockTibetanToEnglish(text)
                    }
                    sourceLang == "en" && targetLang == "bo" -> {
                        // English to Tibetan mock translation
                        mockEnglishToTibetan(text)
                    }
                    else -> text
                }

                TranslationResult(
                    translatedText = translatedText,
                    sourceLanguage = getLanguageName(sourceLang),
                    targetLanguage = getLanguageName(targetLang),
                    confidence = 0.95
                )

            } catch (e: Exception) {
                println("Translation error: ${e.message}")
                e.printStackTrace()
                TranslationResult(
                    translatedText = text,
                    sourceLanguage = getLanguageName(sourceLang),
                    targetLanguage = getLanguageName(targetLang),
                    confidence = 0.0
                )
            }
        }
    }

    private fun mockTibetanToEnglish(tibetanText: String): String {
        // Mock translations for common Tibetan phrases
        val mockTranslations = mapOf(
            "བཀྲ་ཤིས་བདེ་ལེགས།" to "Hello and good fortune!",
            "ཐུགས་རྗེ་ཆེ།" to "Thank you very much.",
            "ཁྱོད་ཀྱི་མིང་ལ་གང་ཟེར་གྱི་ཡོད།" to "What is your name?",
            "ང་ལ་དགའ་པོ་བྱུང་།" to "I am happy.",
            "བོད་སྐད།" to "Tibetan language"
        )

        return mockTranslations[tibetanText] ?: "English translation of: $tibetanText"
    }

    private fun mockEnglishToTibetan(englishText: String): String {
        // Mock translations for common English phrases
        val mockTranslations = mapOf(
            "hello" to "བཀྲ་ཤིས་བདེ་ལེགས།",
            "thank you" to "ཐུགས་རྗེ་ཆེ།",
            "what is your name" to "ཁྱོད་ཀྱི་མིང་ལ་གང་ཟེར་གྱི་ཡོད།",
            "i am happy" to "ང་ལ་དགའ་པོ་བྱུང་།",
            "tibetan" to "བོད་པ།"
        )

        val lowerText = englishText.lowercase().trim()
        return mockTranslations[lowerText] ?: "བོད་སྐད་དུ་བསྒྱུར་བ། $englishText"
    }

    private fun getLanguageName(langCode: String): String {
        return when (langCode) {
            "bo" -> "Tibetan"
            "en" -> "English"
            else -> langCode
        }
    }

    suspend fun rephraseText(text: String): RephraseResult {
        // Simulate API delay
        delay(2000)

        if (text.isEmpty()) {
            return RephraseResult("", "Original", emptyList())
        }

        // Simulate rephrasing with different styles
        val improvements = mutableListOf<String>()
        var rephrasedText = text
        val style: String

        // Choose rephrasing style based on text characteristics
        when {
            text.length < 50 -> {
                style = "Expanded"
                rephrasedText = expandText(text)
                improvements.add("Added detail and clarity")
                improvements.add("Enhanced readability")
            }
            text.contains("very", ignoreCase = true) || text.contains("really", ignoreCase = true) -> {
                style = "Professional"
                rephrasedText = makeProfessional(text)
                improvements.add("Replaced weak modifiers")
                improvements.add("Enhanced formality")
            }
            text.split(" ").size > 20 -> {
                style = "Concise"
                rephrasedText = makeConcise(text)
                improvements.add("Reduced wordiness")
                improvements.add("Improved clarity")
            }
            else -> {
                style = "Enhanced"
                rephrasedText = enhanceText(text)
                improvements.add("Improved word choice")
                improvements.add("Better flow")
            }
        }

        return RephraseResult(rephrasedText, style, improvements)
    }

    private fun expandText(text: String): String {
        val expansions = mapOf(
            "good" to "excellent",
            "bad" to "problematic",
            "big" to "substantial",
            "small" to "compact",
            "nice" to "pleasant",
            "ok" to "satisfactory",
            "cool" to "impressive"
        )

        var expanded = text
        expansions.forEach { (simple, detailed) ->
            expanded = expanded.replace("\\b$simple\\b".toRegex(RegexOption.IGNORE_CASE), detailed)
        }

        // Add context phrases
        if (!expanded.contains("Furthermore") && !expanded.contains("Additionally")) {
            val sentences = expanded.split(". ")
            if (sentences.size > 1) {
                expanded = sentences.first() + ". Additionally, " + sentences.drop(1).joinToString(". ")
            }
        }

        return expanded
    }

    private fun makeProfessional(text: String): String {
        val professionalReplacements = mapOf(
            "very good" to "excellent",
            "really good" to "outstanding",
            "very bad" to "inadequate",
            "really bad" to "unacceptable",
            "very important" to "crucial",
            "really important" to "essential",
            "a lot of" to "numerous",
            "lots of" to "many",
            "kind of" to "somewhat",
            "sort of" to "rather"
        )

        var professional = text
        professionalReplacements.forEach { (casual, formal) ->
            professional = professional.replace(casual, formal, ignoreCase = true)
        }

        return professional
    }

    private fun makeConcise(text: String): String {
        val conciseReplacements = mapOf(
            "in order to" to "to",
            "due to the fact that" to "because",
            "at this point in time" to "now",
            "for the purpose of" to "for",
            "in the event that" to "if",
            "with regard to" to "regarding",
            "take into consideration" to "consider",
            "make a decision" to "decide",
            "come to a conclusion" to "conclude"
        )

        var concise = text
        conciseReplacements.forEach { (verbose, brief) ->
            concise = concise.replace(verbose, brief, ignoreCase = true)
        }

        // Remove redundant words
        concise = concise.replace("\\s+".toRegex(), " ") // Multiple spaces
        concise = concise.replace("that that", "that")
        concise = concise.replace("and and", "and")

        return concise.trim()
    }

    private fun enhanceText(text: String): String {
        val enhancements = mapOf(
            "said" to "mentioned",
            "told" to "informed",
            "got" to "received",
            "made" to "created",
            "went" to "proceeded",
            "came" to "arrived",
            "put" to "placed",
            "give" to "provide",
            "take" to "acquire",
            "see" to "observe"
        )

        var enhanced = text
        enhancements.forEach { (basic, improved) ->
            enhanced = enhanced.replace("\\b$basic\\b".toRegex(RegexOption.IGNORE_CASE), improved)
        }

        return enhanced
    }
}
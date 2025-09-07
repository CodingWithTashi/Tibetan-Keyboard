package com.kharagedition.tibetankeyboard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


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
)

// Translation API Request/Response data classes
@Serializable
data class TranslationRequest(
    val text: String,
    val sourceLang: String,
    val targetLang: String
)

@Serializable
data class TranslationData(
    @SerialName("translatedText")
    val translatedText: String
)

@Serializable
data class UsageInfo(
    @SerialName("charactersUsed")
    val charactersUsed: Int,
    @SerialName("remainingCharacters")
    val remainingCharacters: Int? = null
)

@Serializable
data class TranslationResponse(
    @SerialName("success")
    val success: Boolean,
    @SerialName("data")
    val data: TranslationData,
    @SerialName("usage")
    val usage: UsageInfo
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
@Serializable
data class GeminiChatRequest(
    val message: String,
    val sessionId: String? = null,
    val resetChat: Boolean? = false
)
@Serializable
data class GeminiChatResponse(
    val success: Boolean,
    val data: ChatData? = null,
    val error: String? = null,
    val message: String? = null,
    val usage: UsageInfo? = null
)
@Serializable
data class ChatData(
    val response: String,
    val sessionId: String
)
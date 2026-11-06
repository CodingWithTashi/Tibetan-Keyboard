package com.kharagedition.tibetankeyboard.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reply of `POST /speech-to-text`: `{ success, data: { text } }`, or `{ success: false, error, message }`. */
@Serializable
data class SpeechResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("data") val data: SpeechData? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null,
)

@Serializable
data class SpeechData(
    @SerialName("text") val text: String,
)

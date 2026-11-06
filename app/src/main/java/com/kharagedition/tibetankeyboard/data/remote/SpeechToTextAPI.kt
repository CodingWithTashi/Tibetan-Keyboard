package com.kharagedition.tibetankeyboard.data.remote

import com.kharagedition.tibetankeyboard.data.model.SpeechResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface SpeechToTextAPI {
    /**
     * Sends one recording as the raw request body (`audio/wav`, 16 kHz mono 16-bit, at most
     * 60 s; see `WavEncoder`). [idempotencyKey] is the same for every retry of one recording, so
     * the server never bills it twice.
     */
    @Headers("Accept: application/json")
    @POST("speech-to-text")
    suspend fun transcribe(
        @Header("userid") userId: String,
        @Header("x-idempotency-key") idempotencyKey: String,
        @Body audio: RequestBody,
    ): SpeechResponse
}

package com.kharagedition.tibetankeyboard.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.kharagedition.tibetankeyboard.data.remote.RetrofitClient
import com.kharagedition.tibetankeyboard.data.remote.SpeechToTextAPI
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.FailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** The outcome of writing one recording down. */
sealed interface SpeechResult {
    data class Text(val text: String) : SpeechResult
    data class Failed(val kind: FailureKind) : SpeechResult
}

/** Writes a recording down. The seam [VoiceController] is tested through. */
fun interface Transcriber {
    suspend fun transcribe(wav: ByteArray, idempotencyKey: String): SpeechResult
}

/** Voice typing's server call. Never logs the recording or the transcript. */
class SpeechRepository(
    private val api: SpeechToTextAPI = RetrofitClient.speechAPI,
    private val userId: () -> String = { FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous" },
) : Transcriber {

    override suspend fun transcribe(wav: ByteArray, idempotencyKey: String): SpeechResult =
        withContext(Dispatchers.IO) {
            try {
                val response = api.transcribe(userId(), idempotencyKey, wav.toRequestBody(AUDIO_WAV))
                val text = response.data?.text
                if (response.success && text != null) SpeechResult.Text(text)
                else SpeechResult.Failed(FailureKind.SERVER)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SpeechResult.Failed(SpeechFailures.kindOf(e))
            }
        }

    private companion object {
        val AUDIO_WAV = "audio/wav".toMediaType()
    }
}

package com.kharagedition.tibetankeyboard.data.repository

import com.kharagedition.tibetankeyboard.ui.keyboard.voice.FailureKind
import retrofit2.HttpException
import java.io.IOException

/** Turns a failed upload into what the voice panel shows, and whether Retry is offered. */
object SpeechFailures {

    fun kindOf(t: Throwable): FailureKind = when (t) {
        is HttpException -> when (t.code()) {
            // The server refused this recording (bad or unsupported format, too long): the same
            // bytes would fail again. Only these say the audio is at fault.
            400, 413, 415, 422 -> FailureKind.BAD_AUDIO
            // Everything else (busy, down, a missing route, a refused key) is not the recording's
            // fault, and the recording is kept, so sending it again may work.
            else -> FailureKind.SERVER
        }
        // No connection, a dropped one or a timeout: the recording is kept for Retry.
        is IOException -> FailureKind.OFFLINE
        else -> FailureKind.SERVER
    }
}

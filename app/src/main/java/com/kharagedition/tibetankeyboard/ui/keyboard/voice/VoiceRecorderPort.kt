package com.kharagedition.tibetankeyboard.ui.keyboard.voice

/** One finished recording: a ready-to-send WAV and what the controller needs to judge it. */
class Recording(
    val wav: ByteArray,
    val durationMs: Long,
    /** The loudest moment, 0..1 (see [AudioLevel]); silence reads near 0. */
    val peakLevel: Float,
)

/** What the controller needs from a microphone. The Android one is [AudioRecordRecorder]. */
interface VoiceRecorderPort {

    /** Called on the main thread. */
    interface Listener {
        fun onLevel(level: Float, elapsedMs: Long)

        /** The recording reached [VoicePolicy.MAX_MS] and stopped taking audio. */
        fun onLimitReached()

        /** The microphone stopped working mid-recording (another app took it, a call came in). */
        fun onFailure()
    }

    /** Starts recording; false when the microphone cannot be opened. */
    fun start(listener: Listener): Boolean

    /** Ends the recording and returns it, or null when none is running. */
    suspend fun stop(): Recording?

    /** Ends the recording and throws it away. Safe to call when none is running. */
    fun cancel()
}

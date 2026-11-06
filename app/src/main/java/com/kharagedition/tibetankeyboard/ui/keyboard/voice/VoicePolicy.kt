package com.kharagedition.tibetankeyboard.ui.keyboard.voice

/**
 * The numbers voice typing's limits and feel hang on, in one place so they are tested and a
 * change to one is a change to the rule, not a hunt through views.
 *
 * `MAX_MS` must stay within what the server accepts (`api-backend` `wavInfo.ts`: 60.5 s and
 * 2 MiB); [VoicePolicyTest] guards it.
 */
object VoicePolicy {

    /** A recording stops by itself here. The server refuses anything longer. */
    const val MAX_MS = 60_000L

    /** From here a gold ring fills around the mic, so the stop is never a surprise. */
    const val WARN_MS = 50_000L

    /** Shorter than this is a slip of the finger: nothing is uploaded. */
    const val MIN_SPEECH_MS = 800L

    /** How far to slide left, while holding, to arm cancel. */
    const val CANCEL_SLIDE_DP = 60f

    /**
     * The loudest the recording got ([AudioLevel], 0..1) must reach this to be worth uploading.
     * A quiet room measured about 0.14 on a Pixel 8 and ordinary speech reads 0.5 to 0.75, so
     * 0.3 (about -38 dB) sits well clear of both: a whisper still passes, room noise does not.
     * In a noisy place everything passes, which only costs an upload. Tune on devices.
     */
    const val MIN_PEAK_LEVEL = 0.3f

    /**
     * How a recording was started. People hold the mic to talk ([HOLD]), and recording starts the
     * instant the finger lands, so no first syllable is lost. [TAP] is only the accessibility route:
     * TalkBack cannot hold, so it starts and stops with an action instead.
     */
    enum class Press { TAP, HOLD }

    /** Whether a finished recording holds speech worth sending to the (paid) server. */
    fun shouldUpload(durationMs: Long, peakLevel: Float): Boolean =
        durationMs >= MIN_SPEECH_MS && peakLevel >= MIN_PEAK_LEVEL

    fun isEnding(elapsedMs: Long): Boolean = elapsedMs >= WARN_MS

    /** 0 until [WARN_MS], then up to 1 at [MAX_MS]: how much of the gold ring to draw. */
    fun endingProgress(elapsedMs: Long): Float =
        ((elapsedMs - WARN_MS).toFloat() / (MAX_MS - WARN_MS)).coerceIn(0f, 1f)

    /** [dxDp] is the finger's horizontal travel since it went down; left is negative. */
    fun cancelArmed(dxDp: Float): Boolean = dxDp <= -CANCEL_SLIDE_DP
}

package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePolicy.Press

/**
 * What voice typing reports about itself, so we can see whether it is used, where it fails and
 * what it costs in waiting. Counts, buckets and labels only: never audio, and never a transcript.
 *
 * The controller calls these at the right moments; `AppAnalyticsVoiceEvents` sends them on. A seam
 * of its own so the controller stays free of Android and its reporting is unit-tested.
 */
interface VoiceEvents {

    /** Why a recording stopped. */
    enum class EndReason {
        /** The user tapped the mic again or let go of a hold. */
        FINISHED,

        /** It reached [VoicePolicy.MAX_MS] and stopped by itself. */
        AUTO_STOP,

        /** The user pressed cancel or Back. */
        CANCELLED,

        /** A hold was released after sliding left. */
        SLID_AWAY,

        /** The keyboard closed, or the user moved to another field. */
        KEYBOARD_HIDDEN,
    }

    fun opened()
    fun recordingStarted(press: Press)
    fun recordingEnded(reason: EndReason, durationMs: Long)

    /** [chars] is the length of the text added, [latencyMs] the wait from first send to answer. */
    fun transcribed(chars: Int, latencyMs: Long, retried: Boolean)

    fun failed(kind: FailureKind)
    fun undone()

    object None : VoiceEvents {
        override fun opened() = Unit
        override fun recordingStarted(press: Press) = Unit
        override fun recordingEnded(reason: EndReason, durationMs: Long) = Unit
        override fun transcribed(chars: Int, latencyMs: Long, retried: Boolean) = Unit
        override fun failed(kind: FailureKind) = Unit
        override fun undone() = Unit
    }
}

/** Coarse ranges for analytics: enough to see the shape of usage without logging exact timings. */
object VoiceBuckets {

    fun duration(ms: Long): String = when {
        ms < 3_000 -> "lt3s"
        ms < 10_000 -> "3_10s"
        ms < 30_000 -> "10_30s"
        else -> "30_60s"
    }

    fun latency(ms: Long): String = when {
        ms < 2_000 -> "lt2s"
        ms < 5_000 -> "2_5s"
        ms < 10_000 -> "5_10s"
        else -> "gt10s"
    }
}

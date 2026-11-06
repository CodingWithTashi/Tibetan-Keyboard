package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePolicy.Press

/** Sends what [VoiceController] reports to Firebase Analytics, as labels and coarse buckets. */
object AppAnalyticsVoiceEvents : VoiceEvents {

    override fun opened() = AppAnalytics.logVoiceOpened()

    override fun recordingStarted(press: Press) =
        AppAnalytics.logVoiceRecordingStarted(if (press == Press.HOLD) "hold" else "tap")

    override fun recordingEnded(reason: VoiceEvents.EndReason, durationMs: Long) =
        AppAnalytics.logVoiceRecordingEnded(reason.name.lowercase(), VoiceBuckets.duration(durationMs))

    override fun transcribed(chars: Int, latencyMs: Long, retried: Boolean) =
        AppAnalytics.logVoiceTranscribed(chars, VoiceBuckets.latency(latencyMs), retried)

    override fun failed(kind: FailureKind) = AppAnalytics.logVoiceFailed(kind.name.lowercase())

    override fun undone() = AppAnalytics.logVoiceUndo()
}

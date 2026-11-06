package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import androidx.annotation.StringRes
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePolicy.Press

/**
 * What the voice panel shows for one [VoiceState]: a plain table the view only renders, so every
 * state's copy, controls and motion is decided (and tested) here, not scattered through view
 * code. Invariant: the controls row holds either the orb ([orb]) or one button ([cta]).
 */
data class VoiceContent(
    @StringRes val title: Int,
    val titleTone: Tone = Tone.NORMAL,
    @StringRes val body: Int? = null,
    @StringRes val hint: Int? = null,
    /** The waveform's mode; null hides it (a message or an error takes its room). */
    val wave: WaveMode? = null,
    /** The orb's mode; null hides it, and then [cta] is on screen instead. */
    val orb: OrbMode? = null,
    val cta: Cta? = null,
    /** The title's three pulsing dots ("Listening…"). */
    val dots: Boolean = false,
    val timer: TimerStyle = TimerStyle.NONE,
    val right: RightButton = RightButton.BACKSPACE,
    /** The orb is pressed in and a little larger: a hold in progress. */
    val orbHeld: Boolean = false,
    /** 0..1: how much of the gold ring around the orb is drawn (the last ten seconds). */
    val endingProgress: Float = 0f,
    /** A green check before the title: the dictation is in the field. */
    val confirmed: Boolean = false,
    val retry: Boolean = false,
    /** The edit tools (Undo, Select all, Clear): on whenever the panel is idle with its mic. */
    val tools: Boolean = false,
    /** Undo is live: there is a dictation or a Clear to take back. */
    val canUndo: Boolean = false,
) {
    enum class Tone { NORMAL, WARN }
    enum class WaveMode { IDLE, LIVE, THINK }
    enum class OrbMode { READY, LISTENING, BUSY, CANCEL }
    enum class TimerStyle { NONE, RUNNING, ENDING }
    enum class RightButton { BACKSPACE, CANCEL, NONE }
    enum class Cta(@StringRes val label: Int) {
        ALLOW_MIC(R.string.voice_ask_cta),
        OPEN_SETTINGS(R.string.voice_off_cta),
    }

    companion object {
        /** [elapsedMs] is how long the current recording has run (only read while listening). */
        fun of(state: VoiceState, elapsedMs: Long): VoiceContent = when (state) {
            VoiceState.NeedsPermission -> VoiceContent(
                title = R.string.voice_ask_title, body = R.string.voice_ask_body,
                hint = R.string.voice_ask_hint, cta = Cta.ALLOW_MIC, right = RightButton.NONE,
            )
            VoiceState.PermissionOff -> VoiceContent(
                title = R.string.voice_off_title, body = R.string.voice_off_body,
                hint = R.string.voice_off_hint, cta = Cta.OPEN_SETTINGS, right = RightButton.NONE,
            )
            VoiceState.Ready -> VoiceContent(
                title = R.string.voice_ready_title, body = R.string.voice_ready_body,
                hint = R.string.voice_ready_hint, orb = OrbMode.READY, tools = true,
            )
            is VoiceState.Listening -> listening(state, elapsedMs)
            VoiceState.Transcribing -> VoiceContent(
                title = R.string.voice_busy_title, body = R.string.voice_busy_body,
                hint = R.string.voice_busy_hint, wave = WaveMode.THINK, orb = OrbMode.BUSY,
                right = RightButton.CANCEL,
            )
            VoiceState.Added -> VoiceContent(
                title = R.string.voice_added, hint = R.string.voice_added_hint,
                orb = OrbMode.READY, confirmed = true, tools = true, canUndo = true,
            )
            is VoiceState.Cleared -> VoiceContent(
                title = R.string.voice_cleared_title, body = R.string.voice_cleared_body,
                hint = R.string.voice_cleared_hint, orb = OrbMode.READY, tools = true, canUndo = true,
            )
            is VoiceState.Failure -> failure(state.kind)
        }

        private fun listening(state: VoiceState.Listening, elapsedMs: Long): VoiceContent {
            val ending = VoicePolicy.isEnding(elapsedMs)
            val timer = if (ending) TimerStyle.ENDING else TimerStyle.RUNNING
            val progress = VoicePolicy.endingProgress(elapsedMs)

            if (state.cancelArmed) {
                return VoiceContent(
                    title = R.string.voice_cancel_title, titleTone = Tone.WARN,
                    hint = R.string.voice_cancel_hint, wave = WaveMode.LIVE, orb = OrbMode.CANCEL,
                    timer = timer, orbHeld = true,
                )
            }
            val hint = when {
                ending -> R.string.voice_listening_hint_ending
                state.press == Press.HOLD -> R.string.voice_listening_hint_hold
                else -> R.string.voice_listening_hint_tap
            }
            return VoiceContent(
                title = R.string.voice_listening_title, hint = hint, wave = WaveMode.LIVE,
                orb = OrbMode.LISTENING, dots = true, timer = timer,
                orbHeld = state.press == Press.HOLD, endingProgress = progress,
            )
        }

        private fun failure(kind: FailureKind): VoiceContent = when (kind) {
            FailureKind.NO_SPEECH -> VoiceContent(
                title = R.string.voice_nohear_title, body = R.string.voice_nohear_body,
                hint = R.string.voice_nohear_hint, orb = OrbMode.READY, tools = true,
            )
            FailureKind.TOO_SHORT -> VoiceContent(
                title = R.string.voice_short_title, body = R.string.voice_short_body,
                hint = R.string.voice_ready_hint, orb = OrbMode.READY, tools = true,
            )
            FailureKind.OFFLINE -> VoiceContent(
                title = R.string.voice_offline_title, titleTone = Tone.WARN,
                body = R.string.voice_saved_body, hint = R.string.voice_retry_hint,
                orb = OrbMode.READY, retry = true, tools = true,
            )
            FailureKind.SERVER -> VoiceContent(
                title = R.string.voice_server_title, titleTone = Tone.WARN,
                body = R.string.voice_saved_body, hint = R.string.voice_retry_hint,
                orb = OrbMode.READY, retry = true, tools = true,
            )
            FailureKind.MIC_BUSY -> VoiceContent(
                title = R.string.voice_micbusy_title, titleTone = Tone.WARN,
                body = R.string.voice_micbusy_body, orb = OrbMode.READY, tools = true,
            )
            FailureKind.BAD_AUDIO -> VoiceContent(
                title = R.string.voice_bad_title, titleTone = Tone.WARN,
                body = R.string.voice_bad_body, orb = OrbMode.READY, tools = true,
            )
        }
    }
}

/** Text formats shared by the panel's views. */
object VoiceFormat {

    /** `0:07`, `0:52`, `1:00`: a recording's length. */
    fun timer(elapsedMs: Long): String {
        val seconds = (elapsedMs.coerceAtLeast(0) / 1000).toInt()
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
}

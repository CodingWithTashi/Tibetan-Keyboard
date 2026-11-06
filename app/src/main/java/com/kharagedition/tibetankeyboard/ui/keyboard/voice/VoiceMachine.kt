package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePolicy.Press

/** Why a dictation did not produce text. */
enum class FailureKind(
    /** The recording is kept, so Retry sends it again without a second take. */
    val retryable: Boolean,
) {
    NO_SPEECH(false),

    /** The press was shorter than speech can be: a tap, not a held mic. */
    TOO_SHORT(false),
    OFFLINE(true),
    SERVER(true),
    MIC_BUSY(false),
    BAD_AUDIO(false),
}

/** What the voice panel shows. Exactly one at a time. */
sealed interface VoiceState {
    /** The mic has not been allowed yet; the panel explains and asks. */
    object NeedsPermission : VoiceState

    /** The user refused for good; only the system settings can turn it on. */
    object PermissionOff : VoiceState

    object Ready : VoiceState

    data class Listening(val press: Press, val cancelArmed: Boolean) : VoiceState

    object Transcribing : VoiceState

    /** The dictation was written into the field; the field above shows it, the panel only confirms. */
    object Added : VoiceState

    /** The field was emptied with Clear, which can still be undone. [chars] is how much it held. */
    data class Cleared(val chars: Int) : VoiceState

    data class Failure(val kind: FailureKind) : VoiceState
}

/** Things that happen to the panel: the user's, the recorder's and the server's. */
sealed interface VoiceEvent {
    data class PermissionChecked(val granted: Boolean, val blocked: Boolean) : VoiceEvent
    data class Begin(val press: Press) : VoiceEvent
    data class SlideTo(val armed: Boolean) : VoiceEvent

    /** The recording ended (the user finished it, or it hit [VoicePolicy.MAX_MS]). */
    data class Stop(val durationMs: Long, val peakLevel: Float) : VoiceEvent

    /** Abort: Back, the keyboard closing, the field changing, or the cancel button. */
    object Cancel : VoiceEvent

    /** The field was emptied (Clear). */
    data class ClearedAll(val chars: Int) : VoiceEvent

    data class Transcribed(val text: String) : VoiceEvent
    data class Failed(val kind: FailureKind) : VoiceEvent
    object Retry : VoiceEvent
    object Undone : VoiceEvent
}

/**
 * The voice panel's rules as a pure function, so every state and every way out of it is
 * unit-tested. The controller feeds it events and does the side effects each new state needs
 * (open the mic, upload, commit text); an event that makes no sense in the current state
 * returns that state unchanged, so a late answer or a stray tap can never corrupt the panel.
 */
object VoiceMachine {

    fun initial(micGranted: Boolean): VoiceState =
        if (micGranted) VoiceState.Ready else VoiceState.NeedsPermission

    fun reduce(state: VoiceState, event: VoiceEvent): VoiceState = when (event) {
        is VoiceEvent.PermissionChecked -> onPermission(state, event)
        is VoiceEvent.Begin -> if (canBegin(state)) VoiceState.Listening(event.press, cancelArmed = false) else state
        is VoiceEvent.SlideTo -> onSlide(state, event)
        is VoiceEvent.Stop -> onStop(state, event)
        VoiceEvent.Cancel -> if (state is VoiceState.Listening || state == VoiceState.Transcribing) VoiceState.Ready else state
        is VoiceEvent.Transcribed -> onTranscribed(state, event)
        is VoiceEvent.Failed ->
            if (state is VoiceState.Listening || state == VoiceState.Transcribing) VoiceState.Failure(event.kind) else state
        VoiceEvent.Retry -> if (state is VoiceState.Failure && state.kind.retryable) VoiceState.Transcribing else state
        VoiceEvent.Undone -> if (state == VoiceState.Added || state is VoiceState.Cleared) VoiceState.Ready else state
        is VoiceEvent.ClearedAll -> if (canClear(state)) VoiceState.Cleared(event.chars) else state
    }

    private fun canBegin(state: VoiceState): Boolean =
        state == VoiceState.Ready || state == VoiceState.Added || state is VoiceState.Failure ||
            state is VoiceState.Cleared

    /** Clear is for the idle panel: not while recording, writing, asking, or already cleared. */
    private fun canClear(state: VoiceState): Boolean =
        state == VoiceState.Ready || state == VoiceState.Added || state is VoiceState.Failure

    private fun onPermission(state: VoiceState, event: VoiceEvent.PermissionChecked): VoiceState {
        if (state != VoiceState.NeedsPermission && state != VoiceState.PermissionOff) return state
        return when {
            event.granted -> VoiceState.Ready
            event.blocked -> VoiceState.PermissionOff
            else -> VoiceState.NeedsPermission
        }
    }

    private fun onSlide(state: VoiceState, event: VoiceEvent.SlideTo): VoiceState =
        if (state is VoiceState.Listening && state.press == Press.HOLD) state.copy(cancelArmed = event.armed) else state

    private fun onStop(state: VoiceState, event: VoiceEvent.Stop): VoiceState {
        if (state !is VoiceState.Listening) return state
        return when {
            state.cancelArmed -> VoiceState.Ready
            // A tap on a hold-to-talk mic: say how to use it, not that nothing was heard.
            event.durationMs < VoicePolicy.MIN_SPEECH_MS -> VoiceState.Failure(FailureKind.TOO_SHORT)
            VoicePolicy.shouldUpload(event.durationMs, event.peakLevel) -> VoiceState.Transcribing
            else -> VoiceState.Failure(FailureKind.NO_SPEECH)
        }
    }

    private fun onTranscribed(state: VoiceState, event: VoiceEvent.Transcribed): VoiceState = when {
        state != VoiceState.Transcribing -> state
        event.text.isBlank() -> VoiceState.Failure(FailureKind.NO_SPEECH)
        else -> VoiceState.Added
    }
}

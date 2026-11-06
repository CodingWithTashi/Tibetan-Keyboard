package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import com.kharagedition.tibetankeyboard.data.repository.SpeechResult
import com.kharagedition.tibetankeyboard.data.repository.Transcriber
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePolicy.Press
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** How loud the voice is now and how long the recording has run, for the rings and the timer. */
data class VoiceMeter(val level: Float, val elapsedMs: Long)

/**
 * Runs voice typing: the microphone, the upload and the text field, driven by [VoiceMachine].
 *
 * **Owned by the keyboard service, not a view**: the keyboard rebuilds its views every time it
 * opens, and a recording or an upload must survive that. The panel only observes [state] and
 * [meter] and calls the functions below.
 *
 * Everything runs on one thread (the main one in the app); [recorder] and [transcriber] do their
 * own work off it. An answer that arrives after Cancel is dropped, never typed.
 */
class VoiceController(
    private val recorder: VoiceRecorderPort,
    private val transcriber: Transcriber,
    private val micGranted: () -> Boolean,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
    private val events: VoiceEvents = VoiceEvents.None,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The text field. Implemented by the keyboard service, which owns the input connection. */
    interface Host {
        /**
         * Types [text] into the focused field if it is still [field]. Returns exactly what was
         * inserted (the text plus any separator), or null if the field changed and nothing was.
         */
        fun insert(text: String, field: VoiceField?): String?

        /** Takes [inserted] back out if it is still safe to; true when it was removed. */
        fun undo(inserted: String): Boolean

        /** Selects everything in the field. */
        fun selectAll()

        /** Empties the field. Returns what it held, or null if it was empty or cannot be read. */
        fun clearAll(): String?

        /** Puts [text] back into the (now empty) field; true when it was. */
        fun restore(text: String): Boolean

        /**
         * The panel's delete key: removes the selection if there is one, else one character.
         * Returns the selected text it removed, or null when it deleted a single character
         * (or nothing, in an empty field).
         */
        fun backspace(): String?
    }

    /**
     * What is kept so Retry can send the same recording again, under the same key. [startedAt] is
     * when it was first sent, so the wait reported includes the retries; [attempts] counts sends.
     */
    private class Pending(val wav: ByteArray, val key: String, val startedAt: Long, val attempts: Int)

    var host: Host? = null

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Ready)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _meter = MutableStateFlow(VoiceMeter(0f, 0))
    val meter: StateFlow<VoiceMeter> = _meter.asStateFlow()

    /** The field the current dictation belongs to. */
    var field: VoiceField? = null
        private set

    private var pending: Pending? = null
    private var lastInserted: String? = null

    /** What Clear removed, kept so Undo can put it back; forgotten as soon as anything else happens. */
    private var clearedText: String? = null
    private var transcribeJob: Job? = null
    private var finishing = false

    /** A recording is running or being written down; the keyboard must not discard it. */
    val isBusy: Boolean
        get() = _state.value is VoiceState.Listening || _state.value == VoiceState.Transcribing

    /**
     * Counts recordings. The recorder reports from its own thread through the main handler, so a
     * report can still be queued when its recording is thrown away and the next one starts. Each
     * recording gets its own listener ([listenerFor]) and an old one is ignored, so a late failure
     * cannot cancel the new recorder, nor a late level move the new meter.
     */
    private var session = 0

    private fun listenerFor(id: Int) = object : VoiceRecorderPort.Listener {
        override fun onLevel(level: Float, elapsedMs: Long) {
            if (id == session) _meter.value = VoiceMeter(level, elapsedMs)
        }

        override fun onLimitReached() {
            if (id == session) finish(auto = true)
        }

        override fun onFailure() {
            if (id != session) return
            recorder.cancel()
            dispatch(VoiceEvent.Failed(FailureKind.MIC_BUSY))
        }
    }

    /** The panel was opened on [field]. Starts from a clean state, dropping any earlier work. */
    fun open(field: VoiceField?) {
        discardWork()
        this.field = field
        _meter.value = VoiceMeter(0f, 0)
        lastInserted = null
        clearedText = null
        setState(VoiceMachine.initial(micGranted()))
        events.opened()
    }

    /** The answer to the permission request the panel sent the user to make. */
    fun onPermissionResult(granted: Boolean, blocked: Boolean) =
        dispatch(VoiceEvent.PermissionChecked(granted, blocked))

    /** The user pressed the big mic (a tap or the start of a hold), or opened the panel. */
    fun begin(press: Press) {
        // Already recording: the machine would hand back the same Listening state, which must not
        // be mistaken for a fresh start (that would open the microphone a second time).
        if (_state.value is VoiceState.Listening) return
        val next = VoiceMachine.reduce(_state.value, VoiceEvent.Begin(press))
        if (next !is VoiceState.Listening) return
        if (!micGranted()) {
            setState(VoiceMachine.initial(micGranted = false))
            return
        }
        _meter.value = VoiceMeter(0f, 0)
        if (!recorder.start(listenerFor(++session))) {
            setState(VoiceState.Failure(FailureKind.MIC_BUSY))
            return
        }
        pending = null
        clearedText = null
        setState(next)
        events.recordingStarted(press)
    }

    /** The finger slid left past the cancel line ([armed]) or back. */
    fun slide(armed: Boolean) = dispatch(VoiceEvent.SlideTo(armed))

    /**
     * The recording ended: the user tapped the mic again or let go of a hold, or (with [auto]) it
     * reached [VoicePolicy.MAX_MS] and stopped itself.
     */
    fun finish(auto: Boolean = false) {
        val listening = _state.value as? VoiceState.Listening ?: return
        if (finishing) return

        if (listening.cancelArmed) {
            events.recordingEnded(VoiceEvents.EndReason.SLID_AWAY, _meter.value.elapsedMs)
            session++
            recorder.cancel()
            dispatch(VoiceEvent.Stop(durationMs = 0, peakLevel = 0f)) // armed, so the machine goes Ready
            return
        }

        finishing = true
        val id = session
        scope.launch {
            if (id != session) return@launch // thrown away before this ran: stop() would end the next one
            val recording = recorder.stop()
            finishing = false
            if (id != session || _state.value !is VoiceState.Listening) return@launch // cancelled while stopping

            if (recording == null) {
                dispatch(VoiceEvent.Failed(FailureKind.MIC_BUSY))
                return@launch
            }
            events.recordingEnded(
                if (auto) VoiceEvents.EndReason.AUTO_STOP else VoiceEvents.EndReason.FINISHED,
                recording.durationMs,
            )
            dispatch(VoiceEvent.Stop(recording.durationMs, recording.peakLevel))
            if (_state.value == VoiceState.Transcribing) upload(recording.wav, newKey(), clock(), attempts = 1)
        }
    }

    /**
     * Back, the keyboard closing, the field changing or the cancel button: throw the work away.
     * [reason] is what is reported if a recording was running.
     */
    fun cancel(reason: VoiceEvents.EndReason = VoiceEvents.EndReason.CANCELLED) {
        if (_state.value is VoiceState.Listening) events.recordingEnded(reason, _meter.value.elapsedMs)
        discardWork()
        dispatch(VoiceEvent.Cancel)
    }

    /**
     * The keyboard closed or the field changed: stop a recording that is running. A write-down
     * already under way is left to finish: cancelling it would waste a paid call and lose the
     * user's words, and its answer is dropped anyway if the field is no longer the one it was
     * spoken into (see [Host.insert]).
     */
    fun cancelListening(reason: VoiceEvents.EndReason) {
        if (_state.value is VoiceState.Listening) cancel(reason)
    }

    /** Send the saved recording again, under the same key so it is not billed twice. */
    fun retry() {
        val saved = pending ?: return
        if (_state.value !is VoiceState.Failure) return
        dispatch(VoiceEvent.Retry)
        if (_state.value == VoiceState.Transcribing) {
            upload(saved.wav, saved.key, saved.startedAt, saved.attempts + 1)
        }
    }

    /**
     * Take back the last thing: the dictated text (if it is still safe to remove) or what Clear
     * removed. Either way the result closes and the panel is ready for the next sentence.
     */
    fun undo() {
        when (_state.value) {
            VoiceState.Added -> lastInserted?.let { host?.undo(it) }
            is VoiceState.Cleared -> clearedText?.let { host?.restore(it) }
            else -> return
        }
        lastInserted = null
        clearedText = null
        events.undone()
        dispatch(VoiceEvent.Undone)
    }

    /** Select everything in the field, so the next word spoken or the delete key replaces it. */
    fun selectAll() {
        if (isIdle()) host?.selectAll()
    }

    /** Empty the field. Undo brings it back until the next recording or edit. */
    fun clearAll() {
        val s = _state.value
        if (s != VoiceState.Ready && s != VoiceState.Added && s !is VoiceState.Failure) return
        val previous = host?.clearAll() ?: return
        lastInserted = null // the dictation's own undo would now remove text that is already gone
        clearedText = previous
        dispatch(VoiceEvent.ClearedAll(previous.length))
    }

    /**
     * The panel's delete key. It keeps the result honest about what is now in the field:
     * - a selection (after Select all, say) is a Clear that Undo can bring back;
     * - one character off a fresh dictation means the dictation is no longer what sits before the
     *   cursor, so Undo could not remove it: the "Added" result closes instead of offering a dead button.
     * While recording or writing the key is not on screen, and a stray call does nothing.
     */
    fun backspace() {
        if (!isIdle()) return
        val removed = host?.backspace()
        when {
            !removed.isNullOrEmpty() && _state.value !is VoiceState.Cleared -> {
                lastInserted = null
                clearedText = removed
                dispatch(VoiceEvent.ClearedAll(removed.length))
            }
            _state.value == VoiceState.Added -> {
                lastInserted = null
                dispatch(VoiceEvent.Undone)
            }
        }
    }

    private fun isIdle(): Boolean = _state.value.let {
        it == VoiceState.Ready || it == VoiceState.Added || it is VoiceState.Failure || it is VoiceState.Cleared
    }

    /** The keyboard service is going away. */
    fun release() {
        discardWork()
        scope.cancel()
    }

    private fun upload(wav: ByteArray, key: String, startedAt: Long, attempts: Int) {
        pending = Pending(wav, key, startedAt, attempts)
        transcribeJob?.cancel()
        transcribeJob = scope.launch {
            val result = transcriber.transcribe(wav, key)
            if (_state.value != VoiceState.Transcribing) return@launch // cancelled meanwhile: drop it
            when (result) {
                is SpeechResult.Text -> onText(result.text)
                is SpeechResult.Failed -> dispatch(VoiceEvent.Failed(result.kind))
            }
        }
    }

    private fun onText(text: String) {
        dispatch(VoiceEvent.Transcribed(text))
        if (_state.value != VoiceState.Added) return // nothing was said

        val inserted = host?.insert(text, field)
        if (inserted == null) {
            dispatch(VoiceEvent.Undone) // the field changed: the text belongs nowhere, so drop it
            return
        }
        lastInserted = inserted
        pending?.let { events.transcribed(text.length, clock() - it.startedAt, retried = it.attempts > 1) }
        pending = null
    }

    private fun discardWork() {
        session++ // whatever the recorder still reports now belongs to nobody
        if (_state.value is VoiceState.Listening) recorder.cancel()
        transcribeJob?.cancel()
        transcribeJob = null
        pending = null
        finishing = false
    }

    private fun dispatch(event: VoiceEvent) = setState(VoiceMachine.reduce(_state.value, event))

    /** The one place the state changes, so every failure is reported exactly once. */
    private fun setState(next: VoiceState) {
        val previous = _state.value
        _state.value = next
        if (next is VoiceState.Failure && next != previous) events.failed(next.kind)
    }
}

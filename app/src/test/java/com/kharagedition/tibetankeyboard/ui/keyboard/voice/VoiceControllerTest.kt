package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import com.kharagedition.tibetankeyboard.data.repository.SpeechResult
import com.kharagedition.tibetankeyboard.data.repository.Transcriber
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePolicy.Press
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Added
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Cleared
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Failure
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Listening
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.NeedsPermission
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.PermissionOff
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Ready
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Transcribing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The orchestration: what the panel does with the mic, the network and the text field. */
class VoiceControllerTest {

    private val field = VoiceField.of("com.whatsapp", 7)
    private val wavA = byteArrayOf(1, 2, 3)

    private class FakeRecorder : VoiceRecorderPort {
        var startResult = true
        var startCalls = 0
        var cancelCalls = 0
        var stopCalls = 0
        var recording: Recording? = null
        lateinit var listener: VoiceRecorderPort.Listener

        override fun start(listener: VoiceRecorderPort.Listener): Boolean {
            startCalls++
            this.listener = listener
            return startResult
        }

        override suspend fun stop(): Recording? {
            stopCalls++
            return recording
        }

        override fun cancel() {
            cancelCalls++
        }
    }

    private class FakeTranscriber : Transcriber {
        val calls = mutableListOf<Pair<ByteArray, String>>()
        var next: () -> SpeechResult = { SpeechResult.Text("བཀྲ་ཤིས།") }
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun transcribe(wav: ByteArray, idempotencyKey: String): SpeechResult {
            calls += wav to idempotencyKey
            gate?.await()
            return next()
        }
    }

    private class FakeHost : VoiceController.Host {
        val inserted = mutableListOf<Pair<String, VoiceField?>>()
        var insertResult: ((String) -> String?) = { it }
        var undone = mutableListOf<String>()
        var undoResult = true
        var selectAllCalls = 0
        var clearResult: String? = "bye"
        val restored = mutableListOf<String>()
        var restoreResult = true

        /** What the panel's delete removed: the selected text, or null for a single character. */
        var backspaceResult: String? = null
        var backspaceCalls = 0

        override fun backspace(): String? {
            backspaceCalls++
            return backspaceResult
        }

        override fun selectAll() {
            selectAllCalls++
        }

        override fun clearAll(): String? = clearResult

        override fun restore(text: String): Boolean {
            restored += text
            return restoreResult
        }

        override fun insert(text: String, field: VoiceField?): String? {
            inserted += text to field
            return insertResult(text)
        }

        override fun undo(inserted: String): Boolean {
            undone += inserted
            return undoResult
        }
    }

    private class FakeEvents : VoiceEvents {
        val log = mutableListOf<String>()
        override fun opened() { log += "opened" }
        override fun recordingStarted(press: Press) { log += "started:$press" }
        override fun recordingEnded(reason: VoiceEvents.EndReason, durationMs: Long) { log += "ended:$reason:$durationMs" }
        override fun transcribed(chars: Int, latencyMs: Long, retried: Boolean) { log += "transcribed:$chars:$latencyMs:$retried" }
        override fun failed(kind: FailureKind) { log += "failed:$kind" }
        override fun undone() { log += "undone" }
    }

    private class Rig(micGranted: Boolean = true) {
        val recorder = FakeRecorder().apply { recording = Recording(byteArrayOf(1, 2, 3), 4_000, 0.6f) }
        val transcriber = FakeTranscriber()
        val host = FakeHost()
        val events = FakeEvents()
        var mic = micGranted
        var now = 1_000L
        private var keys = 0
        val controller = VoiceController(
            recorder = recorder,
            transcriber = transcriber,
            micGranted = { mic },
            dispatcher = Dispatchers.Unconfined,
            newKey = { "key-${++keys}" },
            events = events,
            clock = { now },
        ).also { it.host = host }
    }

    private fun Rig.dictate(press: Press = Press.TAP) {
        controller.begin(press)
        controller.finish()
    }

    // --- opening and permission ---

    @Test
    fun opensReadyWhenTheMicIsAllowed() {
        val rig = Rig(micGranted = true)
        rig.controller.open(field)
        assertEquals(Ready, rig.controller.state.value)
    }

    @Test
    fun opensAskingWhenTheMicIsNotAllowed() {
        val rig = Rig(micGranted = false)
        rig.controller.open(field)
        assertEquals(NeedsPermission, rig.controller.state.value)
    }

    @Test
    fun grantingThePermissionMakesThePanelReady() {
        val rig = Rig(micGranted = false)
        rig.controller.open(field)
        rig.controller.onPermissionResult(granted = true, blocked = false)
        assertEquals(Ready, rig.controller.state.value)
    }

    @Test
    fun refusingForGoodShowsPermissionOff() {
        val rig = Rig(micGranted = false)
        rig.controller.open(field)
        rig.controller.onPermissionResult(granted = false, blocked = true)
        assertEquals(PermissionOff, rig.controller.state.value)
    }

    @Test
    fun beginWithoutTheMicNeverTouchesTheRecorder() {
        val rig = Rig(micGranted = false)
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        assertEquals(0, rig.recorder.startCalls)
        assertEquals(NeedsPermission, rig.controller.state.value)
    }

    // --- recording ---

    @Test
    fun beginStartsTheRecorderAndListens() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        assertEquals(1, rig.recorder.startCalls)
        assertEquals(Listening(Press.TAP, false), rig.controller.state.value)
    }

    @Test
    fun aSecondBeginWhileListeningDoesNotStartTheMicTwice() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        rig.controller.begin(Press.HOLD)
        assertEquals(1, rig.recorder.startCalls)
    }

    @Test
    fun aMicThatCannotOpenShowsMicBusyAndRecordsNothing() {
        val rig = Rig()
        rig.recorder.startResult = false
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        assertEquals(Failure(FailureKind.MIC_BUSY), rig.controller.state.value)
        assertEquals(0, rig.transcriber.calls.size)
    }

    @Test
    fun theMeterFollowsTheRecorder() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        rig.recorder.listener.onLevel(0.7f, 1_250)
        assertEquals(VoiceMeter(0.7f, 1_250), rig.controller.meter.value)
    }

    @Test
    fun theMeterResetsForTheNextRecording() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        rig.recorder.listener.onLevel(0.7f, 9_000)
        rig.controller.cancel()
        rig.controller.begin(Press.TAP)
        assertEquals(VoiceMeter(0f, 0), rig.controller.meter.value)
    }

    @Test
    fun theMicFailingMidRecordingShowsMicBusyAndReleasesIt() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        rig.recorder.listener.onFailure()
        assertEquals(Failure(FailureKind.MIC_BUSY), rig.controller.state.value)
        assertEquals(1, rig.recorder.cancelCalls)
    }

    // --- finishing and the answer ---

    @Test
    fun finishingWritesTheSpeechDownAndAddsItToTheField() {
        val rig = Rig()
        rig.controller.open(field)
        rig.dictate()

        assertEquals(1, rig.transcriber.calls.size)
        assertEquals(Added, rig.controller.state.value)
        assertEquals(listOf("བཀྲ་ཤིས།" to field), rig.host.inserted)
    }

    @Test
    fun silenceIsNotUploaded() {
        val rig = Rig()
        rig.recorder.recording = Recording(byteArrayOf(0, 0), 5_000, 0f)
        rig.controller.open(field)
        rig.dictate()

        assertEquals(Failure(FailureKind.NO_SPEECH), rig.controller.state.value)
        assertEquals(0, rig.transcriber.calls.size)
    }

    @Test
    fun aRecorderThatReturnsNothingShowsMicBusy() {
        val rig = Rig()
        rig.recorder.recording = null
        rig.controller.open(field)
        rig.dictate()

        assertEquals(Failure(FailureKind.MIC_BUSY), rig.controller.state.value)
        assertEquals(0, rig.transcriber.calls.size)
    }

    @Test
    fun hittingTheTimeLimitFinishesLikeATap() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        rig.recorder.listener.onLimitReached()

        assertEquals(1, rig.recorder.stopCalls)
        assertEquals(Added, rig.controller.state.value)
    }

    @Test
    fun theLimitAndATapTogetherStopOnlyOnce() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        rig.recorder.listener.onLimitReached()
        rig.controller.finish()

        assertEquals(1, rig.recorder.stopCalls)
        assertEquals(1, rig.transcriber.calls.size)
    }

    @Test
    fun aTranscriptForAFieldTheUserLeftIsDropped() {
        val rig = Rig()
        rig.host.insertResult = { null }
        rig.controller.open(field)
        rig.dictate()

        assertEquals(Ready, rig.controller.state.value)
    }

    @Test
    fun anEmptyAnswerSaysNothingWasHeard() {
        val rig = Rig()
        rig.transcriber.next = { SpeechResult.Text("  ") }
        rig.controller.open(field)
        rig.dictate()

        assertEquals(Failure(FailureKind.NO_SPEECH), rig.controller.state.value)
        assertEquals(0, rig.host.inserted.size)
    }

    // --- cancel ---

    @Test
    fun cancellingWhileListeningThrowsTheRecordingAway() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        rig.controller.cancel()

        assertEquals(1, rig.recorder.cancelCalls)
        assertEquals(Ready, rig.controller.state.value)
        assertEquals(0, rig.transcriber.calls.size)
    }

    @Test
    fun releasingAHoldAfterSlidingLeftThrowsTheRecordingAway() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.HOLD)
        rig.controller.slide(armed = true)
        assertEquals(Listening(Press.HOLD, true), rig.controller.state.value)
        rig.controller.finish()

        assertEquals(1, rig.recorder.cancelCalls)
        assertEquals(0, rig.recorder.stopCalls)
        assertEquals(Ready, rig.controller.state.value)
        assertEquals(0, rig.transcriber.calls.size)
    }

    @Test
    fun slidingBackBeforeReleasingKeepsTheRecording() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.HOLD)
        rig.controller.slide(armed = true)
        rig.controller.slide(armed = false)
        rig.controller.finish()

        assertEquals(Added, rig.controller.state.value)
    }

    @Test
    fun anAnswerThatArrivesAfterCancelIsNeverTyped() {
        val rig = Rig()
        val gate = CompletableDeferred<Unit>()
        rig.transcriber.gate = gate
        rig.controller.open(field)
        rig.dictate()
        assertEquals(Transcribing, rig.controller.state.value)

        rig.controller.cancel()
        gate.complete(Unit)

        assertEquals(Ready, rig.controller.state.value)
        assertEquals(0, rig.host.inserted.size)
    }

    // --- retry ---

    @Test
    fun retrySendsTheSameRecordingWithTheSameKey() {
        val rig = Rig()
        var answers = 0
        rig.transcriber.next = {
            if (answers++ == 0) SpeechResult.Failed(FailureKind.OFFLINE) else SpeechResult.Text("བཀྲ་ཤིས།")
        }
        rig.controller.open(field)
        rig.dictate()
        assertEquals(Failure(FailureKind.OFFLINE), rig.controller.state.value)

        rig.controller.retry()

        assertEquals(2, rig.transcriber.calls.size)
        assertArrayEquals(rig.transcriber.calls[0].first, rig.transcriber.calls[1].first)
        assertEquals("a retry must not be billed twice", rig.transcriber.calls[0].second, rig.transcriber.calls[1].second)
        assertEquals(Added, rig.controller.state.value)
        assertEquals(1, rig.recorder.startCalls)
    }

    @Test
    fun aFreshRecordingGetsAFreshKey() {
        val rig = Rig()
        rig.controller.open(field)
        rig.dictate()
        rig.dictate()

        assertEquals(2, rig.transcriber.calls.size)
        assertNotEquals(rig.transcriber.calls[0].second, rig.transcriber.calls[1].second)
    }

    @Test
    fun retryDoesNothingWhenNothingWasSaved() {
        val rig = Rig()
        rig.recorder.recording = Recording(byteArrayOf(0), 5_000, 0f)
        rig.controller.open(field)
        rig.dictate()
        rig.controller.retry()

        assertEquals(Failure(FailureKind.NO_SPEECH), rig.controller.state.value)
        assertEquals(0, rig.transcriber.calls.size)
    }

    // --- undo ---

    @Test
    fun undoTakesTheAddedTextBack() {
        val rig = Rig()
        rig.host.insertResult = { " $it" }
        rig.controller.open(field)
        rig.dictate()
        rig.controller.undo()

        assertEquals(listOf(" བཀྲ་ཤིས།"), rig.host.undone)
        assertEquals(Ready, rig.controller.state.value)
    }

    @Test
    fun undoWhenTheTextCannotBeSafelyRemovedStillClosesTheResult() {
        val rig = Rig()
        rig.host.undoResult = false
        rig.controller.open(field)
        rig.dictate()
        rig.controller.undo()

        assertEquals(1, rig.host.undone.size)
        assertEquals(Ready, rig.controller.state.value)
    }

    @Test
    fun anotherDictationAfterAResultWorks() {
        val rig = Rig()
        rig.controller.open(field)
        rig.dictate()
        rig.controller.begin(Press.TAP)

        assertEquals(Listening(Press.TAP, false), rig.controller.state.value)
        assertEquals(2, rig.recorder.startCalls)
    }

    // --- closing ---

    @Test
    fun closingWhileListeningReleasesTheMic() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        rig.controller.cancel()

        assertTrue(rig.recorder.cancelCalls >= 1)
    }

    // --- analytics: what the controller reports, and when ---

    @Test
    fun reportsOpeningAndTheStartOfEachRecording() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.HOLD)
        assertEquals(listOf("opened", "started:HOLD"), rig.events.log)
    }

    @Test
    fun aStrayBeginWhileListeningIsNotReported() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        rig.controller.begin(Press.TAP)
        assertEquals(1, rig.events.log.count { it.startsWith("started") })
    }

    @Test
    fun reportsHowARecordingEnded() {
        val finished = Rig().also { it.controller.open(field); it.dictate() }
        assertTrue(finished.events.log.contains("ended:FINISHED:4000"))

        val timedOut = Rig().also {
            it.controller.open(field)
            it.controller.begin(Press.TAP)
            it.recorder.listener.onLimitReached()
        }
        assertTrue(timedOut.events.log.contains("ended:AUTO_STOP:4000"))

        val slid = Rig().also {
            it.controller.open(field)
            it.controller.begin(Press.HOLD)
            it.recorder.listener.onLevel(0.5f, 2_500)
            it.controller.slide(armed = true)
            it.controller.finish()
        }
        assertTrue(slid.events.log.contains("ended:SLID_AWAY:2500"))

        val cancelled = Rig().also {
            it.controller.open(field)
            it.controller.begin(Press.TAP)
            it.recorder.listener.onLevel(0.5f, 1_800)
            it.controller.cancel()
        }
        assertTrue(cancelled.events.log.contains("ended:CANCELLED:1800"))
    }

    @Test
    fun theReasonACancelWasMadeForIsReported() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        rig.recorder.listener.onLevel(0.5f, 900)
        rig.controller.cancel(VoiceEvents.EndReason.KEYBOARD_HIDDEN)
        assertTrue(rig.events.log.contains("ended:KEYBOARD_HIDDEN:900"))
    }

    @Test
    fun cancellingAWaitIsNotARecordingEnding() {
        val rig = Rig()
        rig.transcriber.gate = CompletableDeferred()
        rig.controller.open(field)
        rig.dictate()
        rig.events.log.clear()
        rig.controller.cancel()
        assertEquals(emptyList<String>(), rig.events.log)
    }

    @Test
    fun reportsTheTranscriptAndHowLongItTook() {
        val rig = Rig()
        rig.transcriber.next = { rig.now += 2_300; SpeechResult.Text("བཀྲ་ཤིས།") }
        rig.controller.open(field)
        rig.dictate()
        // 4 Tibetan characters ("བཀྲ" is 3 letters and a subjoined mark, then the tsheg...) are not
        // the point: the event reports the length of what was added, and the wait.
        val event = rig.events.log.single { it.startsWith("transcribed") }
        assertEquals("transcribed:${"བཀྲ་ཤིས།".length}:2300:false", event)
    }

    @Test
    fun aRetryIsReportedAsOneAndItsWaitCountsFromTheFirstSend() {
        val rig = Rig()
        var answers = 0
        rig.transcriber.next = {
            rig.now += 1_000
            if (answers++ == 0) SpeechResult.Failed(FailureKind.OFFLINE) else SpeechResult.Text("བཀྲ་ཤིས།")
        }
        rig.controller.open(field)
        rig.dictate()
        rig.now += 4_000 // the user reads the error before tapping Retry
        rig.controller.retry()

        val event = rig.events.log.single { it.startsWith("transcribed") }
        assertEquals("transcribed:${"བཀྲ་ཤིས།".length}:6000:true", event)
    }

    @Test
    fun reportsEachFailureOnce() {
        val offline = Rig()
        offline.transcriber.next = { SpeechResult.Failed(FailureKind.OFFLINE) }
        offline.controller.open(field)
        offline.dictate()
        assertEquals(1, offline.events.log.count { it == "failed:OFFLINE" })

        val silent = Rig()
        silent.recorder.recording = Recording(byteArrayOf(0), 5_000, 0f)
        silent.controller.open(field)
        silent.dictate()
        assertEquals(1, silent.events.log.count { it == "failed:NO_SPEECH" })

        val busy = Rig()
        busy.recorder.startResult = false
        busy.controller.open(field)
        busy.controller.begin(Press.TAP)
        assertEquals(1, busy.events.log.count { it == "failed:MIC_BUSY" })
    }

    @Test
    fun reportsUndo() {
        val rig = Rig()
        rig.controller.open(field)
        rig.dictate()
        rig.controller.undo()
        assertTrue(rig.events.log.contains("undone"))
    }

    // --- the keyboard hiding or the field changing ---

    @Test
    fun hidingTheKeyboardStopsARecordingAndSaysWhy() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.TAP)
        rig.recorder.listener.onLevel(0.4f, 1_200)
        rig.controller.cancelListening(VoiceEvents.EndReason.KEYBOARD_HIDDEN)

        assertEquals(1, rig.recorder.cancelCalls)
        assertEquals(Ready, rig.controller.state.value)
        assertTrue(rig.events.log.contains("ended:KEYBOARD_HIDDEN:1200"))
    }

    @Test
    fun hidingTheKeyboardLetsAWriteDownAlreadyUnderWayFinish() {
        // Cancelling would waste a paid call and lose the user's words; the answer is typed if
        // they come back to the same field, and dropped if they went elsewhere.
        val rig = Rig()
        val gate = CompletableDeferred<Unit>()
        rig.transcriber.gate = gate
        rig.controller.open(field)
        rig.dictate()
        rig.controller.cancelListening(VoiceEvents.EndReason.KEYBOARD_HIDDEN)
        assertEquals(Transcribing, rig.controller.state.value)

        gate.complete(Unit)

        assertEquals(Added, rig.controller.state.value)
        assertEquals(1, rig.host.inserted.size)
    }

    @Test
    fun anAnswerForAFieldTheUserLeftIsStillDroppedAfterTheKeyboardHid() {
        val rig = Rig()
        val gate = CompletableDeferred<Unit>()
        rig.transcriber.gate = gate
        rig.host.insertResult = { null } // the field check fails: the user is somewhere else now
        rig.controller.open(field)
        rig.dictate()
        rig.controller.cancelListening(VoiceEvents.EndReason.KEYBOARD_HIDDEN)
        gate.complete(Unit)

        assertEquals(Ready, rig.controller.state.value)
    }

    @Test
    fun hidingTheKeyboardWhenIdleChangesNothing() {
        val rig = Rig()
        rig.controller.open(field)
        rig.events.log.clear()
        rig.controller.cancelListening(VoiceEvents.EndReason.KEYBOARD_HIDDEN)

        assertEquals(Ready, rig.controller.state.value)
        assertEquals(0, rig.recorder.cancelCalls)
        assertEquals(emptyList<String>(), rig.events.log)
    }

    // --- hold to talk: a press too short to be speech ---

    @Test
    fun aPressTooShortToBeSpeechAsksToHoldLongerAndUploadsNothing() {
        val rig = Rig()
        rig.recorder.recording = Recording(byteArrayOf(0, 0), 300, 0.9f)
        rig.controller.open(field)
        rig.dictate(Press.HOLD)

        assertEquals(Failure(FailureKind.TOO_SHORT), rig.controller.state.value)
        assertEquals(0, rig.transcriber.calls.size)
    }

    // --- the edit tools: select all, clear, and taking either back ---

    @Test
    fun selectAllAsksTheField() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.selectAll()
        assertEquals(1, rig.host.selectAllCalls)
        assertEquals(Ready, rig.controller.state.value)
    }

    @Test
    fun selectAllIsIgnoredWhileRecording() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.HOLD)
        rig.controller.selectAll()
        assertEquals(0, rig.host.selectAllCalls)
    }

    @Test
    fun clearEmptiesTheFieldAndOffersUndo() {
        val rig = Rig()
        rig.host.clearResult = "བཀྲ་ཤིས།"
        rig.controller.open(field)
        rig.controller.clearAll()
        assertEquals(Cleared("བཀྲ་ཤིས།".length), rig.controller.state.value)
    }

    @Test
    fun clearOnAnEmptyFieldChangesNothing() {
        val rig = Rig()
        rig.host.clearResult = null
        rig.controller.open(field)
        rig.controller.clearAll()
        assertEquals(Ready, rig.controller.state.value)
    }

    @Test
    fun clearIsIgnoredWhileRecordingOrWriting() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.HOLD)
        rig.controller.clearAll()
        assertEquals(Listening(Press.HOLD, false), rig.controller.state.value)

        val gated = Rig()
        gated.transcriber.gate = CompletableDeferred()
        gated.controller.open(field)
        gated.dictate()
        gated.controller.clearAll()
        assertEquals(Transcribing, gated.controller.state.value)
    }

    @Test
    fun undoAfterAClearPutsTheTextBack() {
        val rig = Rig()
        rig.host.clearResult = "བཀྲ་ཤིས།"
        rig.controller.open(field)
        rig.controller.clearAll()
        rig.controller.undo()

        assertEquals(listOf("བཀྲ་ཤིས།"), rig.host.restored)
        assertEquals(Ready, rig.controller.state.value)
    }

    @Test
    fun undoAfterAClearStillClosesTheResultWhenTheTextCannotBePutBack() {
        val rig = Rig()
        rig.host.restoreResult = false
        rig.controller.open(field)
        rig.controller.clearAll()
        rig.controller.undo()

        assertEquals(1, rig.host.restored.size)
        assertEquals(Ready, rig.controller.state.value)
    }

    @Test
    fun aNewRecordingAfterAClearForgetsWhatWasCleared() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.clearAll()
        rig.controller.begin(Press.HOLD)
        rig.controller.cancel()
        rig.controller.undo()

        assertEquals(0, rig.host.restored.size)
    }

    @Test
    fun clearingAfterADictationReplacesItsUndoWithTheClearsUndo() {
        val rig = Rig()
        rig.host.clearResult = "everything"
        rig.controller.open(field)
        rig.dictate()
        rig.controller.clearAll()
        rig.controller.undo()

        assertEquals(listOf("everything"), rig.host.restored)
        assertEquals("the dictation's own undo is gone", 0, rig.host.undone.size)
    }

    // --- the panel's delete button keeps the result honest ---

    @Test
    fun deletingACharacterAfterADictationClosesTheResultSoUndoIsNotADeadButton() {
        val rig = Rig()
        rig.controller.open(field)
        rig.dictate()
        rig.controller.backspace()

        assertEquals(1, rig.host.backspaceCalls)
        assertEquals(Ready, rig.controller.state.value)

        rig.controller.undo()
        assertEquals("the dictation is no longer what is before the cursor", 0, rig.host.undone.size)
    }

    @Test
    fun deletingASelectionIsAClearThatUndoCanBringBack() {
        val rig = Rig()
        rig.host.backspaceResult = "བཀྲ་ཤིས་བདེ་ལེགས།"
        rig.controller.open(field)
        rig.dictate()
        rig.controller.backspace()

        assertEquals(Cleared("བཀྲ་ཤིས་བདེ་ལེགས།".length), rig.controller.state.value)

        rig.controller.undo()
        assertEquals(listOf("བཀྲ་ཤིས་བདེ་ལེགས།"), rig.host.restored)
        assertEquals(Ready, rig.controller.state.value)
        assertEquals("the dictation's own undo went with the text", 0, rig.host.undone.size)
    }

    @Test
    fun deletingASelectionOnAnIdlePanelOffersUndoToo() {
        val rig = Rig()
        rig.host.backspaceResult = "selected"
        rig.controller.open(field)
        rig.controller.backspace()

        assertEquals(Cleared("selected".length), rig.controller.state.value)
    }

    @Test
    fun deletingACharacterOnAReadyPanelChangesNothing() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.backspace()

        assertEquals(1, rig.host.backspaceCalls)
        assertEquals(Ready, rig.controller.state.value)
    }

    @Test
    fun deletingOnAClearedPanelKeepsTheUndo() {
        val rig = Rig()
        rig.host.clearResult = "everything"
        rig.controller.open(field)
        rig.controller.clearAll()
        rig.controller.backspace() // the field is empty: a held delete key keeps repeating

        assertEquals(Cleared("everything".length), rig.controller.state.value)
        rig.controller.undo()
        assertEquals(listOf("everything"), rig.host.restored)
    }

    @Test
    fun deleteIsIgnoredWhileRecordingOrWriting() {
        val rig = Rig()
        rig.controller.open(field)
        rig.controller.begin(Press.HOLD)
        rig.controller.backspace()
        assertEquals(0, rig.host.backspaceCalls)
        assertEquals(Listening(Press.HOLD, false), rig.controller.state.value)

        val gated = Rig()
        gated.transcriber.gate = CompletableDeferred()
        gated.controller.open(field)
        gated.dictate()
        gated.controller.backspace()
        assertEquals(0, gated.host.backspaceCalls)
        assertEquals(Transcribing, gated.controller.state.value)
    }
}


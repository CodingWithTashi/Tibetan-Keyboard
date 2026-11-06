package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvent.Begin
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvent.Cancel
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvent.ClearedAll
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvent.Failed
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvent.PermissionChecked
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvent.Retry
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvent.SlideTo
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvent.Stop
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvent.Transcribed
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvent.Undone
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePolicy.Press
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Added
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Cleared
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Failure
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Listening
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.NeedsPermission
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.PermissionOff
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Ready
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState.Transcribing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every state of the voice panel and the only ways to move between them. */
class VoiceMachineTest {

    private fun step(state: VoiceState, vararg events: VoiceEvent): VoiceState =
        events.fold(state) { s, e -> VoiceMachine.reduce(s, e) }

    private val speech = Stop(durationMs = 4_000, peakLevel = 0.6f)

    // --- permission ---

    @Test
    fun withTheMicAllowedThePanelStartsReady() {
        assertEquals(Ready, VoiceMachine.initial(micGranted = true))
    }

    @Test
    fun withoutTheMicThePanelAsksFirst() {
        assertEquals(NeedsPermission, VoiceMachine.initial(micGranted = false))
    }

    @Test
    fun grantingTheMicMakesThePanelReady() {
        assertEquals(Ready, step(NeedsPermission, PermissionChecked(granted = true, blocked = false)))
    }

    @Test
    fun denyingOnceKeepsAskingAndDenyingForGoodSendsToSettings() {
        assertEquals(NeedsPermission, step(NeedsPermission, PermissionChecked(granted = false, blocked = false)))
        assertEquals(PermissionOff, step(NeedsPermission, PermissionChecked(granted = false, blocked = true)))
    }

    @Test
    fun turningTheMicOnInSettingsRecoversFromPermissionOff() {
        assertEquals(Ready, step(PermissionOff, PermissionChecked(granted = true, blocked = false)))
    }

    @Test
    fun aPermissionAnswerDoesNotDisturbARecording() {
        val listening = Listening(Press.TAP, cancelArmed = false)
        assertEquals(listening, step(listening, PermissionChecked(granted = false, blocked = true)))
    }

    @Test
    fun cannotRecordWithoutPermission() {
        assertEquals(NeedsPermission, step(NeedsPermission, Begin(Press.TAP)))
        assertEquals(PermissionOff, step(PermissionOff, Begin(Press.HOLD)))
    }

    // --- recording ---

    @Test
    fun aTapStartsATapRecordingAndAHoldStartsAHoldRecording() {
        assertEquals(Listening(Press.TAP, false), step(Ready, Begin(Press.TAP)))
        assertEquals(Listening(Press.HOLD, false), step(Ready, Begin(Press.HOLD)))
    }

    @Test
    fun anotherRecordingCanStartAfterAResultOrAFailure() {
        assertEquals(Listening(Press.TAP, false), step(Added, Begin(Press.TAP)))
        for (kind in FailureKind.values()) {
            assertEquals("after $kind", Listening(Press.TAP, false), step(Failure(kind), Begin(Press.TAP)))
        }
    }

    @Test
    fun aSecondPressWhileListeningOrWritingChangesNothing() {
        val listening = Listening(Press.TAP, false)
        assertEquals(listening, step(listening, Begin(Press.HOLD)))
        assertEquals(Transcribing, step(Transcribing, Begin(Press.TAP)))
    }

    @Test
    fun slidingLeftWhileHoldingArmsCancelAndSlidingBackDisarmsIt() {
        val armed = step(Ready, Begin(Press.HOLD), SlideTo(armed = true))
        assertEquals(Listening(Press.HOLD, cancelArmed = true), armed)
        assertEquals(Listening(Press.HOLD, cancelArmed = false), step(armed, SlideTo(armed = false)))
    }

    @Test
    fun slidingDoesNothingForATapRecordingOrOutsideRecording() {
        val tap = Listening(Press.TAP, false)
        assertEquals(tap, step(tap, SlideTo(armed = true)))
        assertEquals(Ready, step(Ready, SlideTo(armed = true)))
    }

    // --- finishing ---

    @Test
    fun finishingRealSpeechStartsWritingItDown() {
        assertEquals(Transcribing, step(Ready, Begin(Press.TAP), speech))
        assertEquals(Transcribing, step(Ready, Begin(Press.HOLD), speech))
    }

    @Test
    fun aPressThatWasTooShortAsksToHoldLongerAndUploadsNothing() {
        // Hold to talk: a tap is a press of a few hundred milliseconds. It is not "nothing heard".
        val slip = Stop(durationMs = 200, peakLevel = 0.9f)
        assertEquals(Failure(FailureKind.TOO_SHORT), step(Ready, Begin(Press.HOLD), slip))
        val justUnder = Stop(durationMs = VoicePolicy.MIN_SPEECH_MS - 1, peakLevel = 0.9f)
        assertEquals(Failure(FailureKind.TOO_SHORT), step(Ready, Begin(Press.HOLD), justUnder))
    }

    @Test
    fun aLongEnoughPressWithSilenceSaysNothingWasHeardAndUploadsNothing() {
        val silent = Stop(durationMs = 5_000, peakLevel = 0f)
        assertEquals(Failure(FailureKind.NO_SPEECH), step(Ready, Begin(Press.HOLD), silent))
    }

    @Test
    fun releasingWhileCancelIsArmedThrowsTheRecordingAway() {
        assertEquals(Ready, step(Ready, Begin(Press.HOLD), SlideTo(true), speech))
    }

    @Test
    fun cancelThrowsAwayARecordingOrAWait() {
        assertEquals(Ready, step(Ready, Begin(Press.TAP), Cancel))
        assertEquals(Ready, step(Ready, Begin(Press.TAP), speech, Cancel))
    }

    @Test
    fun cancelLeavesAResultAndAnErrorAlone() {
        assertEquals(Added, step(Added, Cancel))
        assertEquals(Failure(FailureKind.OFFLINE), step(Failure(FailureKind.OFFLINE), Cancel))
    }

    @Test
    fun stopOutsideARecordingChangesNothing() {
        assertEquals(Ready, step(Ready, speech))
        assertEquals(Transcribing, step(Transcribing, speech))
    }

    // --- the answer ---

    @Test
    fun theTranscriptArrivesAsAnAddedResult() {
        assertEquals(Added, step(Transcribing, Transcribed("བཀྲ་ཤིས།")))
    }

    @Test
    fun anEmptyTranscriptMeansNothingWasHeard() {
        assertEquals(Failure(FailureKind.NO_SPEECH), step(Transcribing, Transcribed("")))
        assertEquals(Failure(FailureKind.NO_SPEECH), step(Transcribing, Transcribed("   ")))
    }

    @Test
    fun aLateAnswerAfterCancelIsDropped() {
        val cancelled = step(Ready, Begin(Press.TAP), speech, Cancel)
        assertEquals(Ready, step(cancelled, Transcribed("too late")))
    }

    @Test
    fun aFailureWhileWritingOrRecordingShowsTheError() {
        assertEquals(Failure(FailureKind.OFFLINE), step(Transcribing, Failed(FailureKind.OFFLINE)))
        assertEquals(Failure(FailureKind.MIC_BUSY), step(Listening(Press.TAP, false), Failed(FailureKind.MIC_BUSY)))
    }

    @Test
    fun aStrayFailureWhileIdleIsIgnored() {
        assertEquals(Ready, step(Ready, Failed(FailureKind.SERVER)))
    }

    // --- retry and undo ---

    @Test
    fun onlyFailuresWithASavedRecordingCanBeRetried() {
        assertTrue(FailureKind.OFFLINE.retryable)
        assertTrue(FailureKind.SERVER.retryable)
        assertFalse(FailureKind.NO_SPEECH.retryable)
        assertFalse(FailureKind.TOO_SHORT.retryable)
        assertFalse(FailureKind.MIC_BUSY.retryable)
        assertFalse(FailureKind.BAD_AUDIO.retryable)
    }

    @Test
    fun retryWritesTheSavedRecordingDownAgain() {
        assertEquals(Transcribing, step(Failure(FailureKind.OFFLINE), Retry))
        assertEquals(Transcribing, step(Failure(FailureKind.SERVER), Retry))
    }

    @Test
    fun retryDoesNothingWhenThereIsNothingSaved() {
        assertEquals(Failure(FailureKind.NO_SPEECH), step(Failure(FailureKind.NO_SPEECH), Retry))
        assertEquals(Ready, step(Ready, Retry))
    }

    @Test
    fun undoReturnsToReadyOnlyFromAResult() {
        assertEquals(Ready, step(Added, Undone))
        assertEquals(Transcribing, step(Transcribing, Undone))
    }

    // --- a whole dictation ---

    @Test
    fun aWholeDictationWithOneRetryEndsAdded() {
        val end = step(
            Ready,
            Begin(Press.TAP), speech, Failed(FailureKind.OFFLINE), Retry, Transcribed("བཀྲ་ཤིས།"),
        )
        assertEquals(Added, end)
    }

    // --- clearing the field ---

    @Test
    fun clearingTheFieldShowsClearedWithItsUndo() {
        assertEquals(Cleared(12), step(Ready, ClearedAll(12)))
        assertEquals(Cleared(12), step(Added, ClearedAll(12)))
        for (kind in FailureKind.values()) {
            assertEquals("after $kind", Cleared(3), step(Failure(kind), ClearedAll(3)))
        }
    }

    @Test
    fun clearingIsIgnoredWhileRecordingOrWritingOrAsking() {
        val listening = Listening(Press.HOLD, false)
        assertEquals(listening, step(listening, ClearedAll(5)))
        assertEquals(Transcribing, step(Transcribing, ClearedAll(5)))
        assertEquals(NeedsPermission, step(NeedsPermission, ClearedAll(5)))
        assertEquals(PermissionOff, step(PermissionOff, ClearedAll(5)))
    }

    @Test
    fun undoingAClearReturnsToReady() {
        assertEquals(Ready, step(Cleared(12), Undone))
    }

    @Test
    fun aNewRecordingCanStartAfterAClear() {
        assertEquals(Listening(Press.HOLD, false), step(Cleared(4), Begin(Press.HOLD)))
    }

    @Test
    fun cancelAndStrayEventsLeaveTheClearedResultAlone() {
        assertEquals(Cleared(4), step(Cleared(4), Cancel))
        assertEquals(Cleared(4), step(Cleared(4), speech))
        assertEquals(Cleared(4), step(Cleared(4), Transcribed("late")))
    }
}


package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import com.kharagedition.tibetankeyboard.R
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the panel shows in each state; the view only renders this table. */
class VoiceContentTest {

    private fun content(state: VoiceState, elapsedMs: Long = 0) = VoiceContent.of(state, elapsedMs)

    private val everyState: List<VoiceState> = listOf(
        NeedsPermission, PermissionOff, Ready,
        Listening(Press.TAP, false), Listening(Press.HOLD, false), Listening(Press.HOLD, true),
        Transcribing, Added, Cleared(5),
    ) + FailureKind.values().map { Failure(it) }

    // --- invariants over every state ---

    @Test
    fun everyStateHasATitle() {
        for (state in everyState) assertTrue("$state", content(state).title != 0)
    }

    @Test
    fun exactlyOneOfTheOrbOrAButtonIsOnScreen() {
        for (state in everyState) {
            val c = content(state)
            assertTrue("$state shows the orb or a CTA, not both and not neither", (c.orb != null) != (c.cta != null))
        }
    }

    @Test
    fun retryIsOfferedOnlyWhereTheRecordingWasKept() {
        for (kind in FailureKind.values()) {
            assertEquals("$kind", kind.retryable, content(Failure(kind)).retry)
        }
        for (state in everyState.filter { it !is Failure }) assertFalse("$state", content(state).retry)
    }

    // --- permission ---

    @Test
    fun beforeTheMicIsAllowedThePanelExplainsAndOffersToAsk() {
        val c = content(NeedsPermission)
        assertEquals(R.string.voice_ask_title, c.title)
        assertEquals(R.string.voice_ask_body, c.body)
        assertEquals(VoiceContent.Cta.ALLOW_MIC, c.cta)
        assertNull(c.orb)
    }

    @Test
    fun afterARefusalForGoodThePanelOffersTheSettings() {
        val c = content(PermissionOff)
        assertEquals(R.string.voice_off_title, c.title)
        assertEquals(VoiceContent.Cta.OPEN_SETTINGS, c.cta)
        assertNull(c.orb)
    }

    // --- ready and recording ---

    @Test
    fun readyAsksTheUserToHoldTheMic() {
        val c = content(Ready)
        assertEquals(R.string.voice_ready_title, c.title)
        assertEquals(R.string.voice_ready_body, c.body)
        assertEquals(R.string.voice_ready_hint, c.hint)
        assertEquals(VoiceContent.OrbMode.READY, c.orb)
        assertNull("the waveform only shows while someone is speaking", c.wave)
    }

    @Test
    fun theEditToolsAreOnScreenWheneverThePanelIsIdle() {
        val idle = listOf(Ready, Added, Cleared(3)) + FailureKind.values().map { Failure(it) }
        for (state in idle) assertTrue("$state", content(state).tools)
    }

    @Test
    fun theEditToolsAreHiddenWhileAskingRecordingOrWriting() {
        val busy = listOf(
            NeedsPermission, PermissionOff, Listening(Press.HOLD, false), Listening(Press.HOLD, true), Transcribing,
        )
        for (state in busy) assertFalse("$state", content(state).tools)
    }

    @Test
    fun undoIsOfferedOnlyAfterADictationOrAClear() {
        assertTrue(content(Added).canUndo)
        assertTrue(content(Cleared(3)).canUndo)
        for (state in everyState.filter { it !is Added && it !is Cleared }) assertFalse("$state", content(state).canUndo)
    }

    @Test
    fun theToolsNeverAppearWithoutTheOrb() {
        for (state in everyState) {
            val c = content(state)
            if (c.tools) assertNotNull("$state", c.orb)
        }
    }

    @Test
    fun aClearedFieldSaysSoAndOffersToBringItBack() {
        val c = content(Cleared(12))
        assertEquals(R.string.voice_cleared_title, c.title)
        assertEquals(R.string.voice_cleared_body, c.body)
        assertEquals(R.string.voice_cleared_hint, c.hint)
        assertEquals(VoiceContent.OrbMode.READY, c.orb)
    }

    @Test
    fun aPressTooShortAsksToHoldLongerWithoutScolding() {
        val c = content(Failure(FailureKind.TOO_SHORT))
        assertEquals(R.string.voice_short_title, c.title)
        assertEquals(R.string.voice_short_body, c.body)
        assertEquals(VoiceContent.Tone.NORMAL, c.titleTone)
        assertEquals(R.string.voice_ready_hint, c.hint)
        assertFalse(c.retry)
    }

    @Test
    fun aTapRecordingShowsTheTimerAndTheLiveWave() {
        val c = content(Listening(Press.TAP, false), elapsedMs = 7_000)
        assertEquals(R.string.voice_listening_title, c.title)
        assertEquals(R.string.voice_listening_hint_tap, c.hint)
        assertEquals(VoiceContent.OrbMode.LISTENING, c.orb)
        assertEquals(VoiceContent.WaveMode.LIVE, c.wave)
        assertEquals(VoiceContent.TimerStyle.RUNNING, c.timer)
        assertTrue(c.dots)
        assertFalse(c.orbHeld)
    }

    @Test
    fun theLastTenSecondsTurnTheTimerGoldAndFillTheRing() {
        val before = content(Listening(Press.TAP, false), elapsedMs = VoicePolicy.WARN_MS - 1)
        assertEquals(VoiceContent.TimerStyle.RUNNING, before.timer)
        assertEquals(R.string.voice_listening_hint_tap, before.hint)
        assertEquals(0f, before.endingProgress, 0f)

        val ending = content(Listening(Press.TAP, false), elapsedMs = 55_000)
        assertEquals(VoiceContent.TimerStyle.ENDING, ending.timer)
        assertEquals(R.string.voice_listening_hint_ending, ending.hint)
        assertEquals(0.5f, ending.endingProgress, 0.0001f)
    }

    @Test
    fun aHoldShowsThePressedOrbAndHowToSendOrCancel() {
        val c = content(Listening(Press.HOLD, false), elapsedMs = 3_000)
        assertTrue(c.orbHeld)
        assertEquals(R.string.voice_listening_hint_hold, c.hint)
        assertEquals(VoiceContent.OrbMode.LISTENING, c.orb)
    }

    @Test
    fun slidingToCancelTurnsTheOrbRedAndSaysSo() {
        val c = content(Listening(Press.HOLD, true), elapsedMs = 4_000)
        assertEquals(VoiceContent.OrbMode.CANCEL, c.orb)
        assertEquals(R.string.voice_cancel_title, c.title)
        assertEquals(VoiceContent.Tone.WARN, c.titleTone)
        assertEquals(R.string.voice_cancel_hint, c.hint)
        assertFalse("no pulsing dots on the cancel prompt", c.dots)
    }

    // --- writing it down and the result ---

    @Test
    fun whileWritingItDownTheOrbIsBusyAndTheRightButtonCancels() {
        val c = content(Transcribing)
        assertEquals(R.string.voice_busy_title, c.title)
        assertEquals(VoiceContent.OrbMode.BUSY, c.orb)
        assertEquals(VoiceContent.WaveMode.THINK, c.wave)
        assertEquals(VoiceContent.RightButton.CANCEL, c.right)
    }

    @Test
    fun afterADictationThePanelConfirmsAndKeepsTheMicReadyForTheNextSentence() {
        val c = content(Added)
        assertEquals(R.string.voice_added, c.title)
        assertTrue("a green check says the text is in the field", c.confirmed)
        assertEquals(R.string.voice_added_hint, c.hint)
        assertEquals(VoiceContent.OrbMode.READY, c.orb)
        assertEquals(VoiceContent.RightButton.BACKSPACE, c.right)
        assertNull("the text is in the field above: the panel does not repeat it", c.wave)
    }

    @Test
    fun onlyTheAddedPanelCarriesTheCheck() {
        for (state in everyState.filter { it != Added }) assertFalse("$state", content(state).confirmed)
    }

    // --- failures ---

    @Test
    fun silenceSaysNothingWasHeardAndNothingWasSent() {
        val c = content(Failure(FailureKind.NO_SPEECH))
        assertEquals(R.string.voice_nohear_title, c.title)
        assertEquals(VoiceContent.Tone.NORMAL, c.titleTone)
        assertEquals(R.string.voice_nohear_hint, c.hint)
    }

    @Test
    fun offlineAndServerFailuresWarnAndKeepTheRecording() {
        val offline = content(Failure(FailureKind.OFFLINE))
        assertEquals(R.string.voice_offline_title, offline.title)
        assertEquals(VoiceContent.Tone.WARN, offline.titleTone)
        assertEquals(R.string.voice_saved_body, offline.body)
        assertEquals(R.string.voice_retry_hint, offline.hint)

        val server = content(Failure(FailureKind.SERVER))
        assertEquals(R.string.voice_server_title, server.title)
        assertEquals(R.string.voice_saved_body, server.body)
    }

    @Test
    fun aBusyMicAndABadRecordingWarnWithoutPromisingASavedOne() {
        assertEquals(R.string.voice_micbusy_title, content(Failure(FailureKind.MIC_BUSY)).title)
        assertEquals(R.string.voice_bad_title, content(Failure(FailureKind.BAD_AUDIO)).title)
        for (kind in listOf(FailureKind.MIC_BUSY, FailureKind.BAD_AUDIO)) {
            val c = content(Failure(kind))
            assertEquals(VoiceContent.Tone.WARN, c.titleTone)
            assertNotNull(c.body)
            assertFalse("$kind", c.body == R.string.voice_saved_body)
        }
    }
}

class VoiceFormatTest {

    @Test
    fun theTimerReadsMinutesAndSeconds() {
        assertEquals("0:00", VoiceFormat.timer(0))
        assertEquals("0:07", VoiceFormat.timer(7_000))
        assertEquals("0:52", VoiceFormat.timer(52_900))
        assertEquals("1:00", VoiceFormat.timer(60_000))
        assertEquals("1:05", VoiceFormat.timer(65_000))
    }

    @Test
    fun theTimerNeverGoesNegative() {
        assertEquals("0:00", VoiceFormat.timer(-500))
    }
}

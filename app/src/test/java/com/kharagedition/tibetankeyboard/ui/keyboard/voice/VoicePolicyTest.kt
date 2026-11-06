package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the numbers voice typing's feel and limits hang on. */
class VoicePolicyTest {

    @Test
    fun aRecordingStopsByItselfAtOneMinute() {
        assertEquals(60_000L, VoicePolicy.MAX_MS)
    }

    @Test
    fun theGoldRingStartsTenSecondsBeforeTheEnd() {
        assertEquals(10_000L, VoicePolicy.MAX_MS - VoicePolicy.WARN_MS)
    }

    @Test
    fun theServerAcceptsEverythingThePhoneCanRecord() {
        // api-backend wavInfo.ts: MAX_AUDIO_MS = 60_500 and MAX_AUDIO_BYTES = 2 MiB. If the phone
        // could record more than the server takes, the last seconds would be refused.
        assertTrue(VoicePolicy.MAX_MS <= 60_500L)
        assertTrue(WavEncoder.byteCountFor(VoicePolicy.MAX_MS) + WavEncoder.HEADER_BYTES <= 2 * 1024 * 1024)
    }

    @Test
    fun aSlipOfTheFingerIsNotUploaded() {
        assertFalse(VoicePolicy.shouldUpload(durationMs = 300, peakLevel = 0.9f))
        assertFalse(VoicePolicy.shouldUpload(durationMs = VoicePolicy.MIN_SPEECH_MS - 1, peakLevel = 0.9f))
    }

    @Test
    fun silenceIsNotUploaded() {
        assertFalse(VoicePolicy.shouldUpload(durationMs = 5_000, peakLevel = 0f))
        assertFalse(VoicePolicy.shouldUpload(durationMs = 5_000, peakLevel = VoicePolicy.MIN_PEAK_LEVEL - 0.001f))
    }

    @Test
    fun aQuietRoomIsNotSpeech() {
        // Measured on a Pixel 8 in an ordinary quiet room: the loudest instant of a silent
        // recording reached about 0.14. Uploading that would bill for nothing, every time.
        assertFalse(VoicePolicy.shouldUpload(durationMs = 5_000, peakLevel = 0.14f))
        assertFalse(VoicePolicy.shouldUpload(durationMs = 5_000, peakLevel = 0.2f))
    }

    @Test
    fun quietSpeechIsStillSpeech() {
        // About -39 dB: someone speaking softly, away from the phone.
        assertTrue(VoicePolicy.shouldUpload(durationMs = 3_000, peakLevel = 0.32f))
    }

    @Test
    fun realSpeechIsUploaded() {
        assertTrue(VoicePolicy.shouldUpload(durationMs = VoicePolicy.MIN_SPEECH_MS, peakLevel = VoicePolicy.MIN_PEAK_LEVEL))
        assertTrue(VoicePolicy.shouldUpload(durationMs = 4_000, peakLevel = 0.6f))
        assertTrue(VoicePolicy.shouldUpload(durationMs = VoicePolicy.MAX_MS, peakLevel = 1f))
    }

    @Test
    fun theEndingRingIsEmptyUntilFiftySecondsThenFillsToOne() {
        assertEquals(0f, VoicePolicy.endingProgress(0), 0f)
        assertEquals(0f, VoicePolicy.endingProgress(VoicePolicy.WARN_MS), 0f)
        assertEquals(0.5f, VoicePolicy.endingProgress(55_000), 0.0001f)
        assertEquals(1f, VoicePolicy.endingProgress(VoicePolicy.MAX_MS), 0f)
        assertEquals("Never past full", 1f, VoicePolicy.endingProgress(90_000), 0f)
    }

    @Test
    fun isEndingFromFiftySeconds() {
        assertFalse(VoicePolicy.isEnding(49_999))
        assertTrue(VoicePolicy.isEnding(50_000))
    }

    @Test
    fun slidingSixtyDpLeftArmsCancel() {
        assertFalse(VoicePolicy.cancelArmed(0f))
        assertFalse(VoicePolicy.cancelArmed(-59.9f))
        assertTrue(VoicePolicy.cancelArmed(-60f))
        assertTrue(VoicePolicy.cancelArmed(-200f))
    }

    @Test
    fun slidingRightNeverArmsCancel() {
        assertFalse(VoicePolicy.cancelArmed(80f))
    }
}

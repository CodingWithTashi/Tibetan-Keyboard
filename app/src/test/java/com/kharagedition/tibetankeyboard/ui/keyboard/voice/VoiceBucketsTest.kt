package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** Analytics ranges: coarse on purpose, and stable, since dashboards group on these labels. */
class VoiceBucketsTest {

    @Test
    fun recordingLengthFallsIntoFourRanges() {
        assertEquals("lt3s", VoiceBuckets.duration(0))
        assertEquals("lt3s", VoiceBuckets.duration(2_999))
        assertEquals("3_10s", VoiceBuckets.duration(3_000))
        assertEquals("3_10s", VoiceBuckets.duration(9_999))
        assertEquals("10_30s", VoiceBuckets.duration(10_000))
        assertEquals("10_30s", VoiceBuckets.duration(29_999))
        assertEquals("30_60s", VoiceBuckets.duration(30_000))
        assertEquals("30_60s", VoiceBuckets.duration(VoicePolicy.MAX_MS))
    }

    @Test
    fun theWaitFallsIntoFourRanges() {
        assertEquals("lt2s", VoiceBuckets.latency(1_999))
        assertEquals("2_5s", VoiceBuckets.latency(2_000))
        assertEquals("2_5s", VoiceBuckets.latency(4_999))
        assertEquals("5_10s", VoiceBuckets.latency(5_000))
        assertEquals("5_10s", VoiceBuckets.latency(9_999))
        assertEquals("gt10s", VoiceBuckets.latency(10_000))
    }
}

package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The level drives the glow rings and the waveform; it must read 0 in silence and 1 when loud. */
class AudioLevelTest {

    /** 16-bit little-endian PCM of one constant sample value. */
    private fun constant(value: Int, samples: Int = 800): ByteArray {
        val out = ByteArray(samples * 2)
        for (i in 0 until samples) {
            out[2 * i] = (value and 0xFF).toByte()
            out[2 * i + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun silenceIsZero() {
        val pcm = constant(0)
        assertEquals(0f, AudioLevel.fromPcm(pcm, pcm.size), 0f)
    }

    @Test
    fun aFullScaleSignalIsOne() {
        val pcm = constant(32_767)
        assertEquals(1f, AudioLevel.fromPcm(pcm, pcm.size), 0.0001f)
    }

    @Test
    fun aNegativeFullScaleSignalIsAlsoOne() {
        val pcm = constant(-32_768)
        assertEquals(1f, AudioLevel.fromPcm(pcm, pcm.size), 0.0001f)
    }

    @Test
    fun aSignalTwentyDecibelsBelowFullScaleIsThreeQuarters() {
        // -20 dB is 10% of full scale. The scale runs -50 dB (0) to -10 dB (1): (-20 + 50) / 40.
        val pcm = constant(3_277)
        assertEquals(0.75f, AudioLevel.fromPcm(pcm, pcm.size), 0.01f)
    }

    @Test
    fun belowTheFloorIsZeroAndAboveTheCeilingIsOne() {
        assertEquals(0f, AudioLevel.fromRms(0.001), 0f) // -60 dB
        assertEquals(1f, AudioLevel.fromRms(0.5), 0f) // -6 dB
    }

    @Test
    fun louderNeverReadsQuieter() {
        var previous = -1f
        for (rms in listOf(0.0, 0.003, 0.01, 0.03, 0.1, 0.3, 1.0)) {
            val level = AudioLevel.fromRms(rms)
            assertTrue("level fell at rms=$rms", level >= previous)
            previous = level
        }
    }

    @Test
    fun anEmptyBufferIsZero() {
        assertEquals(0f, AudioLevel.fromPcm(ByteArray(0), 0), 0f)
        assertEquals(0f, AudioLevel.fromPcm(ByteArray(10), 0), 0f)
    }

    @Test
    fun onlyTheFirstByteCountBytesAreRead() {
        val pcm = constant(32_767, samples = 100) + constant(0, samples = 100)
        // Half of the buffer is loud; reading only the quiet tail's length from the front is loud.
        assertEquals(1f, AudioLevel.fromPcm(pcm, byteCount = 200), 0.0001f)
        assertEquals("a count past the buffer is clamped", AudioLevel.fromPcm(pcm, pcm.size), AudioLevel.fromPcm(pcm, pcm.size * 3), 0f)
    }

    @Test
    fun aTrailingHalfSampleIsIgnored() {
        val pcm = constant(32_767, samples = 10) + byteArrayOf(0x7F)
        assertEquals(1f, AudioLevel.fromPcm(pcm, pcm.size), 0.0001f)
    }
}

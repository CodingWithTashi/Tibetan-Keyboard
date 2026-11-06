package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The server (api-backend `wavInfo.ts`) accepts exactly this header and nothing else. */
class WavEncoderTest {

    private fun le(wav: ByteArray) = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

    private fun ascii(wav: ByteArray, at: Int) = String(wav, at, 4, Charsets.US_ASCII)

    @Test
    fun theHeaderIsACanonicalPcmWavAt16kHzMono16Bit() {
        val pcm = ByteArray(32_000) { (it % 100).toByte() }
        val wav = WavEncoder.wrap(pcm)
        val b = le(wav)

        assertEquals("RIFF", ascii(wav, 0))
        assertEquals(36 + pcm.size, b.getInt(4))
        assertEquals("WAVE", ascii(wav, 8))
        assertEquals("fmt ", ascii(wav, 12))
        assertEquals(16, b.getInt(16))
        assertEquals("PCM", 1, b.getShort(20).toInt())
        assertEquals("mono", 1, b.getShort(22).toInt())
        assertEquals(16_000, b.getInt(24))
        assertEquals("byte rate", 32_000, b.getInt(28))
        assertEquals("block align", 2, b.getShort(32).toInt())
        assertEquals("bits", 16, b.getShort(34).toInt())
        assertEquals("data", ascii(wav, 36))
        assertEquals(pcm.size, b.getInt(40))
    }

    @Test
    fun theSamplesFollowTheHeaderUntouched() {
        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6)
        val wav = WavEncoder.wrap(pcm)

        assertEquals(WavEncoder.HEADER_BYTES + pcm.size, wav.size)
        assertArrayEquals(pcm, wav.copyOfRange(WavEncoder.HEADER_BYTES, wav.size))
    }

    @Test
    fun onlyTheFirstLengthBytesOfTheBufferAreWritten() {
        // The recorder reuses one buffer; the tail past `length` is stale.
        val pcm = byteArrayOf(9, 9, 9, 9, 7, 7)
        val wav = WavEncoder.wrap(pcm, length = 4)

        assertEquals(WavEncoder.HEADER_BYTES + 4, wav.size)
        assertEquals(4, le(wav).getInt(40))
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), wav.copyOfRange(WavEncoder.HEADER_BYTES, wav.size))
    }

    @Test
    fun anEmptyRecordingIsJustAHeader() {
        val wav = WavEncoder.wrap(ByteArray(0))
        assertEquals(WavEncoder.HEADER_BYTES, wav.size)
        assertEquals(0, le(wav).getInt(40))
    }

    @Test
    fun aLengthPastTheBufferIsClamped() {
        val wav = WavEncoder.wrap(ByteArray(4), length = 100)
        assertEquals(WavEncoder.HEADER_BYTES + 4, wav.size)
    }

    @Test
    fun aTrailingHalfSampleIsDropped() {
        val wav = WavEncoder.wrap(ByteArray(5))
        assertEquals("16-bit samples are two bytes", WavEncoder.HEADER_BYTES + 4, wav.size)
    }

    @Test
    fun durationAndByteCountConvertBothWays() {
        assertEquals(32_000, WavEncoder.byteCountFor(1_000))
        assertEquals(1_000L, WavEncoder.durationMs(32_000))
        assertEquals(60_000L, WavEncoder.durationMs(WavEncoder.byteCountFor(60_000)))
        assertEquals(0L, WavEncoder.durationMs(0))
    }

    @Test
    fun byteCountIsAlwaysWholeSamples() {
        assertEquals(0, WavEncoder.byteCountFor(0) % 2)
        assertEquals(0, WavEncoder.byteCountFor(333) % 2)
    }
}

package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wraps raw microphone samples in the one WAV layout the server takes: PCM, 16 kHz, mono,
 * 16-bit (`api-backend` `wavInfo.ts` rejects everything else). It is also the only format the
 * Monlam docs show.
 */
object WavEncoder {

    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val HEADER_BYTES = 44

    private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    private const val BYTE_RATE = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE
    private const val FMT_CHUNK_BYTES = 16
    private const val FORMAT_PCM: Short = 1

    /** Whole-sample byte count of [durationMs] of audio. */
    fun byteCountFor(durationMs: Long): Int =
        (durationMs * SAMPLE_RATE / 1000).toInt() * BYTES_PER_SAMPLE

    fun durationMs(pcmBytes: Int): Long = pcmBytes.toLong() * 1000 / BYTE_RATE

    /**
     * A WAV of the first [length] bytes of [pcm]. The recorder reuses one buffer, so bytes past
     * [length] are stale; a trailing half sample is dropped.
     */
    fun wrap(pcm: ByteArray, length: Int = pcm.size): ByteArray {
        val n = length.coerceIn(0, pcm.size) and 1.inv()
        val out = ByteBuffer.allocate(HEADER_BYTES + n).order(ByteOrder.LITTLE_ENDIAN)
        out.put(TAG_RIFF).putInt(HEADER_BYTES - 8 + n).put(TAG_WAVE)
        out.put(TAG_FMT).putInt(FMT_CHUNK_BYTES)
        out.putShort(FORMAT_PCM).putShort(CHANNELS.toShort())
        out.putInt(SAMPLE_RATE).putInt(BYTE_RATE)
        out.putShort((CHANNELS * BYTES_PER_SAMPLE).toShort()).putShort(BITS_PER_SAMPLE.toShort())
        out.put(TAG_DATA).putInt(n)
        out.put(pcm, 0, n)
        return out.array()
    }

    private val TAG_RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
    private val TAG_WAVE = "WAVE".toByteArray(Charsets.US_ASCII)
    private val TAG_FMT = "fmt ".toByteArray(Charsets.US_ASCII)
    private val TAG_DATA = "data".toByteArray(Charsets.US_ASCII)
}

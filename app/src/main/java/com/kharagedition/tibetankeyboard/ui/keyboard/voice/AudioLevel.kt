package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How loud the microphone is, as 0..1, for the glow rings and the waveform. Loudness is
 * logarithmic, so the scale is in decibels: [FLOOR_DB] (a quiet room) reads 0 and [CEIL_DB]
 * (speaking close to the phone) reads 1. Linear amplitude would barely move for ordinary speech.
 */
object AudioLevel {

    const val FLOOR_DB = -50.0
    const val CEIL_DB = -10.0

    private const val FULL_SCALE = 32_768.0

    /** [rms] is the root-mean-square of the samples as a fraction of full scale (0..1). */
    fun fromRms(rms: Double): Float {
        if (rms <= 0.0) return 0f
        val db = 20.0 * log10(rms)
        return ((db - FLOOR_DB) / (CEIL_DB - FLOOR_DB)).coerceIn(0.0, 1.0).toFloat()
    }

    /** The level of the first [byteCount] bytes of little-endian 16-bit [pcm]. */
    fun fromPcm(pcm: ByteArray, byteCount: Int): Float {
        val usable = min(byteCount, pcm.size) and 1.inv()
        if (usable <= 0) return 0f

        var sumOfSquares = 0.0
        var i = 0
        while (i < usable) {
            val sample = (pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)
            sumOfSquares += sample.toDouble() * sample
            i += 2
        }
        return fromRms(sqrt(sumOfSquares / (usable / 2)) / FULL_SCALE)
    }
}

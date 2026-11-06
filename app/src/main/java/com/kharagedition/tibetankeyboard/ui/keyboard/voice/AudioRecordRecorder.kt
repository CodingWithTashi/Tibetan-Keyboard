package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The Android microphone for voice typing: 16 kHz mono 16-bit PCM from [AudioRecord], held in
 * memory (60 s is only 1.9 MB) and wrapped in a WAV by [WavEncoder] when it ends.
 *
 * Takes audio focus while recording so music pauses instead of being recorded with the speech.
 * Reads on a background thread and reports on the main thread, as [VoiceRecorderPort] promises.
 * Never writes audio to disk or to the log.
 */
class AudioRecordRecorder(context: Context) : VoiceRecorderPort {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private class Session(val record: AudioRecord, val pcm: ByteArray) {
        @Volatile var running = true
        @Volatile var bytes = 0
        @Volatile var peak = 0f
        lateinit var job: Job
    }

    private var session: Session? = null
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    override fun start(listener: VoiceRecorderPort.Listener): Boolean {
        if (session != null) return false
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val chunkBytes = WavEncoder.byteCountFor(CHUNK_MS)
        val minBuffer = AudioRecord.getMinBufferSize(
            WavEncoder.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                WavEncoder.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, chunkBytes * 4),
            )
        } catch (e: SecurityException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        requestFocus()
        val started = try {
            record.startRecording()
            record.recordingState == AudioRecord.RECORDSTATE_RECORDING
        } catch (e: IllegalStateException) {
            false
        }
        if (!started) {
            record.release()
            abandonFocus()
            return false
        }

        val s = Session(record, ByteArray(WavEncoder.byteCountFor(VoicePolicy.MAX_MS)))
        session = s
        s.job = ioScope.launch { readLoop(s, chunkBytes, listener) }
        return true
    }

    override suspend fun stop(): Recording? {
        val s = session ?: return null
        session = null
        end(s)
        s.job.join()
        s.record.release()
        abandonFocus()
        return Recording(
            wav = WavEncoder.wrap(s.pcm, s.bytes),
            durationMs = WavEncoder.durationMs(s.bytes),
            peakLevel = s.peak,
        )
    }

    override fun cancel() {
        val s = session ?: return
        session = null
        end(s)
        abandonFocus()
        // Release only once the read loop has left, so a blocked read never meets a freed recorder.
        ioScope.launch {
            s.job.join()
            s.record.release()
        }
    }

    /** Tells the read loop to leave, and unblocks it if it is waiting on the microphone. */
    private fun end(s: Session) {
        s.running = false
        try {
            s.record.stop()
        } catch (e: IllegalStateException) {
            // Already stopped.
        }
    }

    private fun readLoop(s: Session, chunkBytes: Int, listener: VoiceRecorderPort.Listener) {
        val chunk = ByteArray(chunkBytes)
        var limitReached = false
        while (s.running) {
            val read = s.record.read(chunk, 0, chunk.size)
            if (read <= 0) {
                if (s.running) main.post { listener.onFailure() }
                break
            }
            val take = min(read, s.pcm.size - s.bytes)
            System.arraycopy(chunk, 0, s.pcm, s.bytes, take)
            s.bytes += take

            val level = AudioLevel.fromPcm(chunk, take)
            if (level > s.peak) s.peak = level
            val elapsedMs = WavEncoder.durationMs(s.bytes)
            main.post { listener.onLevel(level, elapsedMs) }

            if (s.bytes >= s.pcm.size) {
                limitReached = true
                break
            }
        }
        if (limitReached && s.running) main.post { listener.onLimitReached() }
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            )
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private companion object {
        /** How much audio each read takes: also how often the rings and the timer update. */
        const val CHUNK_MS = 50L
    }
}

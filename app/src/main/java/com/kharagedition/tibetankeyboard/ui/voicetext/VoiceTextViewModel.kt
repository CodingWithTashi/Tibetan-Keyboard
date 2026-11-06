package com.kharagedition.tibetankeyboard.ui.voicetext

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.data.local.TypingStatsStore
import com.kharagedition.tibetankeyboard.data.repository.SpeechRepository
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.AppAnalyticsVoiceEvents
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.AudioRecordRecorder
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceController
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceEvents
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceField
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceInsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voice Text: the keyboard's dictation ([VoiceController]) pointed at an in-app note instead of
 * another app's field. The note is plain state here, so the controller's [Host] is a few lines.
 */
class VoiceTextViewModel(app: Application) : AndroidViewModel(app) {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val host = object : VoiceController.Host {
        override fun insert(text: String, field: VoiceField?): String {
            val joined = VoiceInsert.join(_text.value, text)
            _text.value += joined
            creditStreak(text)
            return joined
        }

        override fun undo(inserted: String): Boolean {
            if (!VoiceInsert.canUndo(_text.value, inserted)) return false
            _text.value = _text.value.removeSuffix(inserted)
            return true
        }

        override fun selectAll() = Unit

        override fun clearAll(): String? {
            val previous = _text.value
            if (previous.isEmpty()) return null
            _text.value = ""
            return previous
        }

        override fun restore(text: String): Boolean {
            if (_text.value.isNotEmpty()) return false
            _text.value = text
            return true
        }

        override fun backspace(): String? = null
    }

    val voice = VoiceController(
        recorder = AudioRecordRecorder(app),
        transcriber = SpeechRepository(),
        micGranted = ::hasMicPermission,
        events = AppAnalyticsVoiceEvents,
    ).also {
        it.host = host
        it.open(null)
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** The user typed in the note: Undo/Clear history no longer describes it. */
    fun onEdited(value: String) {
        _text.value = value
    }

    fun clear() = voice.clearAll()
    fun undo() = voice.undo()

    private fun creditStreak(text: String) {
        val chars = VoiceInsert.tibetanChars(text)
        if (chars > 0) {
            TypingStatsStore.getInstance(getApplication()).recordTibetanChars(chars)
                ?.let { AppAnalytics.logStreakMilestone(it) }
        }
    }

    override fun onCleared() {
        voice.cancel()
        voice.release()
    }
}

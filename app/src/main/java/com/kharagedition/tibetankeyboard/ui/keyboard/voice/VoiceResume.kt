package com.kharagedition.tibetankeyboard.ui.keyboard.voice

/**
 * "Listen now" armed when the user taps "Allow microphone", spent when they are back on the field
 * they tapped it on.
 *
 * The keyboard restarts for the system's permission dialog before it returns to that field, so
 * a restart on any other target must leave it armed; only the same field, or the minute running
 * out, ends it. (Resuming on a field the user did not choose would start the mic unasked.)
 */
class VoiceResume {

    private var field: VoiceField? = null
    private var armedAtMs = 0L

    fun arm(field: VoiceField, nowMs: Long) {
        this.field = field
        armedAtMs = nowMs
    }

    fun disarm() {
        field = null
    }

    /** True when the keyboard should start listening now on [current]; that spends it. */
    fun check(current: VoiceField?, nowMs: Long): Boolean {
        val armed = field ?: return false
        if (nowMs - armedAtMs > WINDOW_MS) {
            field = null
            return false
        }
        if (current == null || !current.matches(armed)) return false // not the user's field yet
        field = null
        return true
    }

    companion object {
        /** How long after "Allow microphone" the panel still starts listening by itself. */
        const val WINDOW_MS = 60_000L
    }
}

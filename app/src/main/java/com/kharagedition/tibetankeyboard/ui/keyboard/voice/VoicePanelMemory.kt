package com.kharagedition.tibetankeyboard.ui.keyboard.voice

/**
 * Whether the voice panel was open, and on which field, so it can come back when the keyboard
 * rebuilds its views.
 *
 * The keyboard rebuilds on every `onStartInputView`: whenever an app restarts input, and twice
 * around the system's permission dialog. A panel that only survived while a recording was
 * running lost its ask, its result and its Undo to the first restart. So the panel stays on the
 * same field in any state; it is forgotten when the user closes it, when the keyboard hides
 * (see `TibetanKeyboard.onWindowHidden`), and when input moves to another field.
 */
class VoicePanelMemory {

    private var field: VoiceField? = null

    /** The panel opened on [field] (null when the app is unknown: it is then never restored). */
    fun opened(field: VoiceField?) {
        this.field = field
    }

    fun closed() {
        field = null
    }

    /** Input restarted for [current]: true if the panel should come straight back. */
    fun restoreOn(current: VoiceField?): Boolean {
        val open = field ?: return false
        if (current != null && current.matches(open)) return true
        field = null // another field, or no field: the panel does not follow the user there
        return false
    }
}

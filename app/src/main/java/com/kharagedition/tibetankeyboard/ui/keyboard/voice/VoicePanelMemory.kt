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

    /**
     * Input restarted for [current]: true if the panel should come straight back. [allowed] is
     * whether voice may be used in that field at all (`VoiceAvailability`): a field id is no proof
     * of the same field, since many apps never set one, so a password field of the same app would
     * otherwise look like the field the panel was opened on.
     */
    fun restoreOn(current: VoiceField?, allowed: Boolean): Boolean {
        val open = field ?: return false
        if (allowed && current != null && current.matches(open)) return true
        field = null // another field, no field, or one that must not be dictated into
        return false
    }
}

package com.kharagedition.tibetankeyboard.ui.keyboard

/**
 * What the delete key removes. With text selected (the user chose "select all", or dragged over a
 * word) it removes that selection, as every keyboard does; otherwise the character before the
 * cursor. Without this the key always took the character *before* the selection, so after
 * "select all" it deleted nothing at all.
 */
object DeleteRule {

    enum class Plan { SELECTION, ONE_CHAR }

    /** [selectedText] is what the editor reports as selected, null when it cannot say. */
    fun plan(selectedText: CharSequence?): Plan =
        if (selectedText.isNullOrEmpty()) Plan.ONE_CHAR else Plan.SELECTION
}

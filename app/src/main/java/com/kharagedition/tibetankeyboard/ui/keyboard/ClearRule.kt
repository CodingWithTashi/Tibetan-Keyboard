package com.kharagedition.tibetankeyboard.ui.keyboard

/**
 * Whether Clear may go ahead. Clear selects the whole field, deletes the selection and promises
 * Undo, which can only put back what was read. So it proceeds only when what is *selected* is
 * exactly what was *read* (and short enough to restore). Otherwise it does nothing, rather than:
 * - claim a clear in an editor that ignored "select all" (nothing deleted, and Undo would then
 *   type the text a second time), or
 * - empty a long field of which the editor handed over only a window (the rest lost for good).
 */
object ClearRule {

    /** The most text Clear will empty: Undo can only restore what a capped read returned. */
    const val MAX_UNDOABLE_CLEAR = 9_000

    /** Whether [everything], the field as read, is worth clearing and short enough to restore. */
    fun restorable(everything: String): Boolean =
        everything.isNotEmpty() && everything.length < MAX_UNDOABLE_CLEAR

    /** [selected] is what the editor reports selected now, after the field was selected. */
    fun canClear(everything: String, selected: CharSequence?): Boolean =
        restorable(everything) && selected?.toString() == everything
}

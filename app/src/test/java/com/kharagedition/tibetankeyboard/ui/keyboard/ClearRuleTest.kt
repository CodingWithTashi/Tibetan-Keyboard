package com.kharagedition.tibetankeyboard.ui.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clear selects the field, deletes the selection, and promises Undo. It may only go ahead when what
 * is selected is exactly what was read (and so what Undo can restore): an editor that ignores
 * "select all" would delete nothing yet claim a clear, and one that hands over only a window of a
 * long field would lose the rest for good.
 */
class ClearRuleTest {

    private val text = "བཀྲ་ཤིས་བདེ་ལེགས།"

    @Test
    fun clearsWhenTheWholeFieldWasReadAndIsSelected() {
        assertTrue(ClearRule.canClear(everything = text, selected = text))
    }

    @Test
    fun anEmptyFieldHasNothingToClear() {
        assertFalse(ClearRule.canClear(everything = "", selected = ""))
        assertFalse(ClearRule.canClear(everything = "", selected = null))
    }

    @Test
    fun anEditorThatIgnoredSelectAllIsLeftAlone() {
        assertFalse(ClearRule.canClear(everything = text, selected = null))
        assertFalse(ClearRule.canClear(everything = text, selected = ""))
    }

    @Test
    fun aSelectionOfOnlyPartOfWhatWasReadIsLeftAlone() {
        assertFalse(ClearRule.canClear(everything = text, selected = text.take(5)))
    }

    @Test
    fun aWindowShorterThanTheFieldIsLeftAlone() {
        // The editor returned a window of a longer field, and selecting everything selects more.
        assertFalse(ClearRule.canClear(everything = text, selected = text + "རྗེས་མ།"))
    }

    @Test
    fun aFieldTooLongToRestoreIsLeftAlone() {
        val long = "a".repeat(ClearRule.MAX_UNDOABLE_CLEAR)
        assertFalse(ClearRule.canClear(everything = long, selected = long))
        val justUnder = "a".repeat(ClearRule.MAX_UNDOABLE_CLEAR - 1)
        assertTrue(ClearRule.canClear(everything = justUnder, selected = justUnder))
    }
}

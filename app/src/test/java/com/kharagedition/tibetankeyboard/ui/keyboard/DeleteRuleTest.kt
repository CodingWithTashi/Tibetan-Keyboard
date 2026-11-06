package com.kharagedition.tibetankeyboard.ui.keyboard

import com.kharagedition.tibetankeyboard.ui.keyboard.DeleteRule.Plan
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Found on a phone: after "select all", the delete key did nothing, because it always removed the
 * one character before the selection (and there is none before a selection that starts at 0).
 */
class DeleteRuleTest {

    @Test
    fun withNothingSelectedTheKeyDeletesOneCharacter() {
        assertEquals(Plan.ONE_CHAR, DeleteRule.plan(null))
        assertEquals(Plan.ONE_CHAR, DeleteRule.plan(""))
    }

    @Test
    fun withTextSelectedTheKeyDeletesTheSelection() {
        assertEquals(Plan.SELECTION, DeleteRule.plan("བཀྲ་ཤིས།"))
        assertEquals(Plan.SELECTION, DeleteRule.plan("a"))
    }

    @Test
    fun aSelectionOfOnlySpacesIsStillASelection() {
        assertEquals(Plan.SELECTION, DeleteRule.plan("  "))
    }
}

package com.kharagedition.tibetankeyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A slow editor is asked once, then left alone for the cooldown; a healthy one is never skipped. */
class EditorReadGuardTest {

    private var clock = 1_000L
    private val guard = EditorReadGuard(now = { clock }, slowMs = 250, cooldownMs = 5_000)
    private var calls = 0

    /** An editor that takes [takes] ms to answer. */
    private fun editor(takes: Long, answer: String? = "text") = {
        calls++
        clock += takes
        answer
    }

    @Test
    fun healthyEditorIsAlwaysRead() {
        repeat(20) { assertEquals("text", guard.read(editor(takes = 5))) }
        assertEquals(20, calls)
    }

    @Test
    fun slowAnswerIsStillReturned() {
        assertEquals("text", guard.read(editor(takes = 2_000)))
    }

    @Test
    fun afterASlowReadTheEditorIsNotAskedAgain() {
        guard.read(editor(takes = 2_000))
        repeat(10) { assertNull(guard.read(editor(takes = 2_000))) }
        assertEquals(1, calls)
    }

    @Test
    fun editorIsProbedAgainAfterTheCooldown() {
        guard.read(editor(takes = 2_000))
        clock += 5_000
        assertEquals("text", guard.read(editor(takes = 5)))
        assertEquals(2, calls)
        // The probe was fast, so reads carry on.
        assertEquals("text", guard.read(editor(takes = 5)))
        assertEquals(3, calls)
    }

    @Test
    fun stillSlowAfterTheCooldownBlocksAgain() {
        guard.read(editor(takes = 2_000))
        clock += 5_000
        guard.read(editor(takes = 2_000))
        assertNull(guard.read(editor(takes = 5)))
        assertEquals(2, calls)
    }

    @Test
    fun fastNullAnswerDoesNotBlock() {
        // An editor that just cannot report its text is not a hung one.
        assertNull(guard.read(editor(takes = 5, answer = null)))
        assertEquals("text", guard.read(editor(takes = 5)))
        assertEquals(2, calls)
    }

    @Test
    fun resetForgetsTheSlowEditor() {
        guard.read(editor(takes = 2_000))
        guard.reset()
        assertEquals("text", guard.read(editor(takes = 5)))
        assertEquals(2, calls)
    }
}

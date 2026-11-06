package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import com.kharagedition.tibetankeyboard.ui.keyboard.KeyboardSizing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The voice panel takes the place of the bar and the keys, so its height is [KeyboardSizing]'s.
 * On a short screen, and above all in landscape, that is far less than the design's 360dp; the
 * panel must still fit every state, never clip.
 */
class VoicePanelLayoutTest {

    @Test
    fun aFullHeightPanelIsTheDesignedOne() {
        val spec = VoicePanelLayout.spec(360)
        assertFalse(spec.compact)
        assertEquals(48, spec.headerDp)
        assertEquals(80, spec.orbDp)
        assertEquals(48, spec.sideButtonDp)
        assertEquals(108, spec.controlsDp)
        assertEquals(44, spec.waveformDp)
        assertTrue(spec.hintVisible)
        assertFalse(spec.timerInTitle)
        assertEquals("a full-size touch target for Undo, Select all and Clear", 44, spec.toolsDp)
        assertTrue(spec.showBody)
    }

    @Test
    fun belowThreeHundredDpThePanelGoesCompact() {
        val regular = VoicePanelLayout.spec(VoicePanelLayout.COMPACT_BELOW_DP)
        val compact = VoicePanelLayout.spec(VoicePanelLayout.COMPACT_BELOW_DP - 1)

        assertFalse(regular.compact)
        assertTrue(compact.compact)
        assertEquals(40, compact.headerDp)
        assertEquals(56, compact.orbDp)
        assertEquals(40, compact.sideButtonDp)
        assertEquals(72, compact.controlsDp)
        assertEquals(32, compact.waveformDp)
        assertFalse(compact.hintVisible)
        assertTrue(compact.timerInTitle)
        assertEquals(36, compact.toolsDp)
        assertFalse(compact.showBody)
    }

    @Test
    fun theOrbAlwaysFitsInsideItsControlsRow() {
        for (height in 200..420) {
            val spec = VoicePanelLayout.spec(height)
            assertTrue("orb in controls at $height", spec.orbDp <= spec.controlsDp)
            assertTrue("side buttons in controls at $height", spec.sideButtonDp <= spec.controlsDp)
        }
    }

    @Test
    fun listeningAndEveryIdleScreenFitAtEveryHeightTheKeyboardCanGive() {
        // 230 is the shortest panel (landscape: five 34dp rows + 60); 360 the tallest.
        for (height in 218..420) {
            val spec = VoicePanelLayout.spec(height)
            val stage = VoicePanelLayout.stageDp(height)

            assertTrue(
                "listening needs ${VoicePanelLayout.listeningNeedDp(spec)} but $height gives $stage",
                stage >= VoicePanelLayout.listeningNeedDp(spec),
            )
            assertTrue(
                "the idle screen needs ${VoicePanelLayout.idleNeedDp(spec)} but $height gives $stage",
                stage >= VoicePanelLayout.idleNeedDp(spec),
            )
        }
    }

    @Test
    fun fitsOnEveryScreenTheKeyboardSupports() {
        for (screenDp in listOf(480, 568, 640, 732, 800, 892, 960)) {
            for (landscape in listOf(false, true)) {
                val keyHeight = KeyboardSizing.keyHeightDp(screenDp, landscape)
                val panel = KeyboardSizing.panelHeightDp(keyHeight)
                val spec = VoicePanelLayout.spec(panel)
                val stage = VoicePanelLayout.stageDp(panel)

                assertTrue("listening at ${screenDp}dp landscape=$landscape", stage >= VoicePanelLayout.listeningNeedDp(spec))
                assertTrue("idle at ${screenDp}dp landscape=$landscape", stage >= VoicePanelLayout.idleNeedDp(spec))
            }
        }
    }

    @Test
    fun theShortestLandscapePanelIsCompact() {
        val shortest = KeyboardSizing.panelHeightDp(KeyboardSizing.MIN_KEY_HEIGHT_DP)
        assertEquals(230, shortest)
        assertTrue(VoicePanelLayout.spec(shortest).compact)
    }

    @Test
    fun theStageIsWhatIsLeftAfterTheFixedParts() {
        val spec = VoicePanelLayout.spec(360)
        val expected = 360 - spec.headerDp - spec.controlsDp - VoicePanelLayout.HINT_DP - VoicePanelLayout.BOTTOM_PADDING_DP
        assertEquals(expected, VoicePanelLayout.stageDp(360))

        val compact = VoicePanelLayout.spec(230)
        assertEquals(230 - compact.headerDp - compact.controlsDp - VoicePanelLayout.BOTTOM_PADDING_DP, VoicePanelLayout.stageDp(230))
    }

    @Test
    fun theExtrasAreDroppedBeforeAnythingIsClipped() {
        // Right at the regular/compact boundary the stage is only 108dp: the tools stay, the optional
        // body text (the explanation, or the streak note) goes.
        val tight = VoicePanelLayout.spec(VoicePanelLayout.COMPACT_BELOW_DP)
        assertFalse(tight.showBody)
        assertEquals(44, tight.toolsDp)
    }

    @Test
    fun theToolsAreNeverDropped() {
        for (height in 218..420) assertTrue("at $height", VoicePanelLayout.spec(height).toolsDp >= 36)
    }
}


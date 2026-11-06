package com.kharagedition.tibetankeyboard.ui.keyboard.voice

/**
 * The voice panel's measurements for the height it is given. The panel replaces the bar and the
 * keys, so that height is `KeyboardSizing.panelHeightDp`: 360dp at best, but as little as 230dp
 * in landscape. The regular layout's fixed parts alone take about 190dp, so below
 * [COMPACT_BELOW_DP] the panel shrinks its orb, drops the hint line and moves the timer up to
 * the title, rather than clip. Pure, so [VoicePanelLayoutTest] proves every state fits every
 * height the keyboard can give.
 *
 * The panel never repeats the dictated text: it is already in the field above, so the stage
 * only holds a title, an optional line under it, the waveform while listening, and the tools.
 */
object VoicePanelLayout {

    /** Below this panel height the compact measurements apply. */
    const val COMPACT_BELOW_DP = 300

    const val HINT_DP = 24
    const val BOTTOM_PADDING_DP = 12

    private const val TITLE_DP = 22
    private const val BODY_DP = 46 // two lines of 12.5sp text and its margin
    private const val TIMER_DP = 18
    private const val GAP_DP = 10
    private const val COMPACT_GAP_DP = 8

    private const val TOOLS_DP = 44
    private const val COMPACT_TOOLS_DP = 36

    data class Spec(
        val compact: Boolean,
        val headerDp: Int,
        val orbDp: Int,
        val sideButtonDp: Int,
        val controlsDp: Int,
        val waveformDp: Int,
        val hintVisible: Boolean,
        /** Compact only: the timer shares the title line instead of having its own. */
        val timerInTitle: Boolean,
        /** Height of the Undo / Select all / Clear row. Never dropped: it is the point of the panel. */
        val toolsDp: Int,
        /** The line under the title on idle screens (the explanation, the streak note); dropped when the stage is short. */
        val showBody: Boolean,
    )

    private val COMPACT = Spec(
        compact = true, headerDp = 40, orbDp = 56, sideButtonDp = 40, controlsDp = 72,
        waveformDp = 32, hintVisible = false, timerInTitle = true,
        toolsDp = COMPACT_TOOLS_DP, showBody = false,
    )

    private val REGULAR = Spec(
        compact = false, headerDp = 48, orbDp = 80, sideButtonDp = 48, controlsDp = 108,
        waveformDp = 44, hintVisible = true, timerInTitle = false,
        toolsDp = TOOLS_DP, showBody = false,
    )

    /**
     * The measurements for a panel [panelHeightDp] high. A regular panel gives the idle screens
     * their line under the title when it fits. The extra goes before anything is clipped; the
     * tools never do.
     */
    fun spec(panelHeightDp: Int): Spec {
        if (panelHeightDp < COMPACT_BELOW_DP) return COMPACT
        val body = idleNeedDp(REGULAR.copy(showBody = true)) <= stageDp(panelHeightDp, REGULAR)
        return REGULAR.copy(showBody = body)
    }

    /** Height left for the message and waveform, or the idle screen, once the fixed parts are placed. */
    fun stageDp(panelHeightDp: Int, spec: Spec = spec(panelHeightDp)): Int =
        panelHeightDp - spec.headerDp - spec.controlsDp -
            (if (spec.hintVisible) HINT_DP else 0) - BOTTOM_PADDING_DP

    /** What the listening and writing states need from the stage. */
    fun listeningNeedDp(spec: Spec): Int =
        if (spec.timerInTitle) TITLE_DP + COMPACT_GAP_DP + spec.waveformDp
        else TITLE_DP + TIMER_DP + 2 * GAP_DP + spec.waveformDp

    /**
     * What an idle screen (ready, added, cleared, an error) needs: the title, its line if shown,
     * and the tools.
     */
    fun idleNeedDp(spec: Spec): Int =
        TITLE_DP + (if (spec.showBody) BODY_DP else 0) + gap(spec) + spec.toolsDp

    private fun gap(spec: Spec): Int = if (spec.compact) COMPACT_GAP_DP else GAP_DP
}

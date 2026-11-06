package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.preference.PreferenceManager
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.data.local.TypingStatsStore
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceContent.OrbMode
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceContent.RightButton
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceContent.Tone
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceContent.WaveMode
import com.kharagedition.tibetankeyboard.ui.settings.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The voice typing panel: it takes the place of the bar and the keys while the user dictates.
 *
 * A thin renderer. [VoiceController] decides what happens, [VoiceContent] what each state shows
 * and [VoicePanelLayout] how big each part is; this view only draws that, forwards the taps, and
 * adds the feel (haptics when the Vibrate setting is on, the "Listening…" dots, TalkBack text).
 * It observes the controller while attached; the controller outlives it, because the keyboard
 * rebuilds this view every time it opens.
 */
class VoicePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    /** What the panel cannot do itself: it owns no input connection, window or Activity. */
    interface Host {
        /** The keyboard button: leave voice typing, back to the keys. */
        fun onKeyboardRequested()
        fun onAllowMicrophone()
        fun onOpenSettings()
        fun onBackspace()
    }

    private val density = resources.displayMetrics.density
    private val header: View
    private val headerTimer: TextView
    private val title: TextView
    private val body: TextView
    private val timer: TextView
    private val wave: WaveformView
    private val tools: View
    private val toolFirst: TextView
    private val toolSelectAll: TextView
    private val toolClear: TextView
    private val controls: View
    private val keyboardBtn: ImageView
    private val orb: VoiceOrbView
    private val cta: TextView
    private val rightBtn: ImageView
    private val hint: TextView

    private var controller: VoiceController? = null
    private var host: Host? = null
    private var scope: CoroutineScope? = null
    private var spec = VoicePanelLayout.spec(VoicePanelLayout.COMPACT_BELOW_DP)

    private val checkIcon: Drawable by lazy { icon(R.drawable.ic_voice_check, JADE, 18) }

    private var shown: VoiceContent? = null
    private var shownState: VoiceState? = null

    /** The line under "Added": its streak or first-message note, worked out once per dictation. */
    private var addedNote: String? = null
    private var titleText = ""
    private var dotStep = 0
    private val dotsRunnable = object : Runnable {
        override fun run() {
            dotStep = (dotStep + 1) % 4
            title.text = titleText + ".".repeat(dotStep) + FIGURE_SPACE.repeat(3 - dotStep)
            postDelayed(this, DOTS_MS)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.voice_panel_background)
        LayoutInflater.from(context).inflate(R.layout.voice_panel, this, true)

        header = findViewById(R.id.voice_header)
        headerTimer = findViewById(R.id.voice_header_timer)
        title = findViewById(R.id.voice_title)
        body = findViewById(R.id.voice_body)
        timer = findViewById(R.id.voice_timer)
        wave = findViewById(R.id.voice_wave)
        tools = findViewById(R.id.voice_tools)
        toolFirst = findViewById(R.id.voice_tool_first)
        toolSelectAll = findViewById(R.id.voice_tool_select_all)
        toolClear = findViewById(R.id.voice_tool_clear)
        controls = findViewById(R.id.voice_controls)
        keyboardBtn = findViewById(R.id.voice_keyboard_btn)
        orb = findViewById(R.id.voice_orb)
        cta = findViewById(R.id.voice_cta)
        rightBtn = findViewById(R.id.voice_right_btn)
        hint = findViewById(R.id.voice_hint)

        setChipIcon(toolSelectAll, R.drawable.ic_voice_select_all, CREAM)
        setChipIcon(toolClear, R.drawable.ic_voice_clear, CREAM)

        keyboardBtn.setOnClickListener { host?.onKeyboardRequested() }
        // A click is what TalkBack sends; a finger goes through the touch listener, which repeats.
        rightBtn.setOnClickListener {
            when (shown?.right) {
                RightButton.BACKSPACE -> host?.onBackspace()
                RightButton.CANCEL -> controller?.cancel()
                else -> Unit
            }
        }
        rightBtn.setOnTouchListener { v, event -> onRightButtonTouch(v, event) }
        cta.setOnClickListener {
            when (shown?.cta) {
                VoiceContent.Cta.ALLOW_MIC -> host?.onAllowMicrophone()
                VoiceContent.Cta.OPEN_SETTINGS -> host?.onOpenSettings()
                null -> Unit
            }
        }
        toolFirst.setOnClickListener { if (shown?.retry == true) controller?.retry() else controller?.undo() }
        toolSelectAll.setOnClickListener { controller?.selectAll() }
        toolClear.setOnClickListener { controller?.clearAll() }
        orb.listener = orbListener()
    }

    /** Connects the panel to the controller it shows and the keyboard it lives in. */
    fun bind(controller: VoiceController, host: Host) {
        this.controller = controller
        this.host = host
        if (isAttachedToWindow) observe()
    }

    /** Sizes every part for a panel [heightDp] high (see [VoicePanelLayout]). */
    fun setPanelHeightDp(heightDp: Int) {
        spec = VoicePanelLayout.spec(heightDp)
        layoutParams = (layoutParams ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)).apply {
            height = px(heightDp)
        }
        setPadding(0, 0, 0, px(VoicePanelLayout.BOTTOM_PADDING_DP))
        header.layoutParams.height = px(spec.headerDp)
        controls.layoutParams.height = px(spec.controlsDp)
        orb.layoutParams.width = px(spec.controlsDp)
        orb.layoutParams.height = px(spec.controlsDp)
        orb.orbDiameterDp = spec.orbDp.toFloat()
        for (side in listOf(keyboardBtn, rightBtn)) {
            side.layoutParams.width = px(spec.sideButtonDp)
            side.layoutParams.height = px(spec.sideButtonDp)
        }
        wave.layoutParams.height = px(spec.waveformDp)
        for (chip in listOf(toolFirst, toolSelectAll, toolClear)) chip.minHeight = px(spec.toolsDp)
        (tools.layoutParams as MarginLayoutParams).topMargin = px(if (spec.compact) 8 else 10)
        shown = null // draw again with the new measurements
        controller?.let { apply(it.state.value) }
        requestLayout()
    }

    // --- observing the controller ---

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observe()
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        removeCallbacks(dotsRunnable)
        removeCallbacks(repeatDelete)
        super.onDetachedFromWindow()
    }

    private fun observe() {
        val c = controller ?: return
        scope?.cancel()
        val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = ui
        shown = null
        apply(c.state.value)
        ui.launch { c.state.collect { apply(it) } }
        ui.launch { c.meter.collect { renderMeter(it) } }
    }

    // --- rendering ---

    private fun apply(state: VoiceState) {
        val elapsed = controller?.meter?.value?.elapsedMs ?: 0
        val content = VoiceContent.of(state, elapsed)
        if (state == shownState && content == shown) return

        val previous = shownState
        shownState = state
        shown = content
        render(content, state, previous)
    }

    private fun render(c: VoiceContent, state: VoiceState, previous: VoiceState?) {
        // Title, with its "Listening…" dots.
        removeCallbacks(dotsRunnable)
        titleText = context.getString(c.title)
        title.setTextColor(if (c.titleTone == Tone.WARN) WARN else CREAM)
        title.setCompoundDrawablesRelative(if (c.confirmed) checkIcon else null, null, null, null)
        if (c.dots) {
            dotStep = 3
            title.text = if (Motion.enabled(context)) titleText + "..." else titleText + "…"
            if (Motion.enabled(context)) postDelayed(dotsRunnable, DOTS_MS)
        } else {
            title.text = titleText
        }

        // The line under the title goes first when the stage is short; a screen with no tools (the
        // permission ask) is nothing without its explanation.
        if (c.confirmed && previous != VoiceState.Added) addedNote = noteForAdded()
        val line = if (c.confirmed) addedNote else c.body?.let { context.getString(it) }
        val bodyFits = !c.tools || spec.showBody
        body.visibility = if (line != null && bodyFits) VISIBLE else GONE
        line?.let { body.text = it }

        // Timer: on its own line, or up in the header when the panel is compact.
        val showTimer = c.timer != VoiceContent.TimerStyle.NONE
        val timerView = if (spec.timerInTitle) headerTimer else timer
        timer.visibility = if (showTimer && !spec.timerInTitle) VISIBLE else GONE
        headerTimer.visibility = if (showTimer && spec.timerInTitle) VISIBLE else GONE
        timerView.setTextColor(if (c.timer == VoiceContent.TimerStyle.ENDING) GOLD else CREAM_2)
        timerView.text = VoiceFormat.timer(controller?.meter?.value?.elapsedMs ?: 0)

        // Waveform.
        wave.visibility = if (c.wave != null) VISIBLE else GONE
        c.wave?.let { wave.mode = it }
        if (c.wave == WaveMode.LIVE && previous !is VoiceState.Listening) wave.clear()

        // The edit tools. Retry takes Undo's slot after a failed upload.
        tools.visibility = if (c.tools) VISIBLE else GONE
        styleTools(c)

        // Controls.
        orb.visibility = if (c.orb != null) VISIBLE else GONE
        c.orb?.let {
            orb.render(it, c.orbHeld)
            orb.setEnding(c.endingProgress)
            orb.contentDescription = context.getString(orbDescription(it))
            // TalkBack cannot hold: its double tap records, and the next one stops and writes it down.
            val action = if (it == OrbMode.LISTENING || it == OrbMode.CANCEL) R.string.cd_voice_action_stop
            else R.string.cd_voice_action_record
            ViewCompat.replaceAccessibilityAction(
                orb, AccessibilityActionCompat.ACTION_CLICK, context.getString(action), null,
            )
        }
        cta.visibility = if (c.cta != null) VISIBLE else GONE
        c.cta?.let { cta.setText(it.label) }

        rightBtn.visibility = if (c.right == RightButton.NONE) INVISIBLE else VISIBLE
        when (c.right) {
            RightButton.BACKSPACE -> {
                rightBtn.setImageResource(R.drawable.outline_backspace_24)
                rightBtn.contentDescription = context.getString(R.string.cd_voice_delete)
            }
            RightButton.CANCEL -> {
                rightBtn.setImageResource(R.drawable.ic_close)
                rightBtn.contentDescription = context.getString(R.string.cd_voice_cancel)
            }
            RightButton.NONE -> Unit
        }

        // The hint line goes first when a short panel is short of room. Where there is room its
        // row is kept even when a state has no hint, so the controls above never jump.
        hint.visibility = when {
            !spec.hintVisible -> GONE
            c.hint != null -> VISIBLE
            else -> INVISIBLE
        }
        c.hint?.let { hint.setText(it) }

        feel(state, previous)
    }

    /** The recording's clock, the voice level and the ending ring: the 20-times-a-second part. */
    private fun renderMeter(meter: VoiceMeter) {
        val state = controller?.state?.value as? VoiceState.Listening ?: return
        val content = VoiceContent.of(state, meter.elapsedMs)
        // Entering the last ten seconds changes the timer's colour and the hint: draw it all again.
        if (content.timer != shown?.timer || content.hint != shown?.hint) {
            apply(state)
        } else {
            orb.setEnding(content.endingProgress)
        }
        (if (spec.timerInTitle) headerTimer else timer).text = VoiceFormat.timer(meter.elapsedMs)
        wave.push(meter.level)
        orb.setLevel(meter.level)
    }

    /** What the dictation earned, for the line under "Added": the first one gets its own note. */
    private fun noteForAdded(): String? {
        val first = !VoiceIntro.isSeen(context)
        val streak = TypingStatsStore.getInstance(context).displayStreak()
        if (first) VoiceIntro.markSeen(context)
        return when {
            first -> context.getString(R.string.voice_added_first)
            streak > 0 -> context.getString(R.string.voice_added_streak, streak)
            else -> null
        }
    }

    // --- gestures from the orb ---

    private fun orbListener() = object : VoiceOrbView.Listener {
        override fun onTap() {
            val c = controller ?: return
            when (c.state.value) {
                is VoiceState.Listening -> c.finish()
                VoiceState.Ready, VoiceState.Added, is VoiceState.Failure ->
                    c.begin(VoicePolicy.Press.TAP)
                else -> Unit
            }
        }

        override fun onHoldStart() {
            controller?.begin(VoicePolicy.Press.HOLD)
        }

        override fun onSlide(armed: Boolean) {
            controller?.slide(armed)
        }

        override fun onHoldEnd(cancelled: Boolean) {
            val c = controller ?: return
            if (cancelled) c.slide(true)
            c.finish()
        }
    }

    // --- the edit tools ---

    /** Undo (live only after a dictation or a Clear) or Retry in the first slot; Select all and Clear always. */
    private fun styleTools(c: VoiceContent) {
        if (!c.tools) return
        if (c.retry) {
            toolFirst.setText(R.string.retry)
            styleChip(toolFirst, R.drawable.ic_voice_retry, activated = true, enabled = true)
        } else {
            toolFirst.setText(R.string.voice_tool_undo)
            styleChip(toolFirst, R.drawable.ic_voice_undo, activated = c.canUndo, enabled = c.canUndo)
        }
        styleChip(toolSelectAll, R.drawable.ic_voice_select_all, activated = false, enabled = true)
        styleChip(toolClear, R.drawable.ic_voice_clear, activated = false, enabled = true)
    }

    /** A live action glows gold; one with nothing to act on is dimmed and does not respond. */
    private fun styleChip(chip: TextView, iconRes: Int, activated: Boolean, enabled: Boolean) {
        chip.isActivated = activated
        chip.isEnabled = enabled
        chip.alpha = if (enabled) 1f else DISABLED_ALPHA
        val color = if (activated) GOLD else CREAM
        chip.setTextColor(color)
        setChipIcon(chip, iconRes, color)
    }

    private fun setChipIcon(chip: TextView, iconRes: Int, color: Int) {
        chip.setCompoundDrawablesRelative(icon(iconRes, color, 16), null, null, null)
    }

    // --- backspace: one press deletes one character; held, it repeats and speeds up ---

    private var repeatCount = 0
    private val repeatDelete = object : Runnable {
        override fun run() {
            host?.onBackspace()
            repeatCount++
            postDelayed(this, if (repeatCount > REPEAT_FAST_AFTER) REPEAT_FAST_MS else REPEAT_MS)
        }
    }

    private fun onRightButtonTouch(view: View, event: MotionEvent): Boolean {
        // Cancel (while writing) is a plain click; only backspace is a repeating key.
        if (shown?.right != RightButton.BACKSPACE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.isPressed = true
                host?.onBackspace()
                repeatCount = 0
                postDelayed(repeatDelete, REPEAT_START_MS)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                removeCallbacks(repeatDelete)
            }
        }
        return true
    }

    // --- feel ---

    /** A short tick when listening starts, a confirm when text lands, a bump on failure. */
    private fun feel(state: VoiceState, previous: VoiceState?) {
        if (previous == null || previous::class == state::class) return
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SettingsPrefs.KEY_VIBRATE, false)) return
        val constant = when (state) {
            is VoiceState.Listening -> HapticFeedbackConstants.KEYBOARD_TAP
            VoiceState.Added ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.KEYBOARD_TAP
            is VoiceState.Failure ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
            else -> return
        }
        performHapticFeedback(constant)
    }

    // --- helpers ---

    private fun orbDescription(mode: OrbMode): Int = when (mode) {
        OrbMode.READY -> R.string.cd_voice_start
        OrbMode.LISTENING, OrbMode.CANCEL -> R.string.cd_voice_finish
        OrbMode.BUSY -> R.string.cd_voice_busy
    }

    private fun px(dp: Int): Int = (dp * density).toInt()

    private fun icon(res: Int, color: Int, sizeDp: Int): Drawable =
        DrawableCompat.wrap(ContextCompat.getDrawable(context, res)!!.mutate()).also {
            DrawableCompat.setTint(it, color)
            it.setBounds(0, 0, px(sizeDp), px(sizeDp))
        }

    private companion object {
        const val DOTS_MS = 400L
        const val DISABLED_ALPHA = 0.38f
        const val REPEAT_START_MS = 420L
        const val REPEAT_MS = 70L
        const val REPEAT_FAST_MS = 35L
        const val REPEAT_FAST_AFTER = 12
        const val FIGURE_SPACE = " "
        const val GOLD = 0xFFF2C264.toInt()
        const val INK = 0xFF3A2606.toInt()
        const val CREAM = 0xFFFCF5E8.toInt()
        const val CREAM_2 = 0xFFEBDCC0.toInt()
        const val JADE = 0xFF5BB98C.toInt()
        const val WARN = 0xFFE58A7E.toInt()
    }
}

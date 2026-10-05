package com.kharagedition.tibetankeyboard.ui.home

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.PickerTrigger

/** How the app saw the keyboard get switched on; the `method` of `keyboard_enabled`. */
enum class EnabledVia(val label: String) {
    /** During the trip to the system settings that setup step 1 starts. */
    SETUP("setup"),
    /** Not seen happening: switched on in an older version, or outside setup step 1. */
    OTHER("other"),
}

/**
 * Setup step 1 sends the user to the system's keyboard settings, where this app can't guide them
 * (in September 2026 about four in ten new users who tapped "Enable" never opened the picker).
 * [EnableTrip] follows that trip: it notices the keyboard being switched on while the user is
 * still away, lets Home come back where Android allows it, and has Home open the keyboard picker
 * (step 2) without another tap once the user is back. A user who comes back without switching
 * it on gets nothing extra.
 *
 * @param sdkInt `Build.VERSION.SDK_INT`; see [bringsHomeBack].
 */
class EnableTrip(private val sdkInt: Int) {
    /** A trip is under way: "Enable" was tapped and Home has not been in front since. */
    var pending = false
        private set
    private var switchedOn = false
    private var broughtBack = false

    /**
     * Whether Home can come back by itself. It can below Android 15: there an app may start an
     * activity while off screen if it has one in the task that is on screen, so the settings
     * screen is opened inside Home's task, the way setup wizards of AOSP-based keyboards do. (The
     * platform still has that rule, "process has activity in foreground task", but the public
     * list of exceptions no longer names it.) From Android 15 the activity security model can
     * refuse an app that tries to cover a system screen from below in its own task, so there the
     * settings open in a task of their own, as they always did, and the user comes back unaided.
     */
    val bringsHomeBack: Boolean get() = sdkInt < FIRST_SDK_WITHOUT_AUTO_RETURN

    /** The user tapped "Enable" and is leaving for the system settings. */
    fun start() {
        pending = true
        switchedOn = false
        broughtBack = false
    }

    /**
     * Home was re-created from saved state (the process died while the user was in the system
     * settings) and that state says a trip was under way. A trip already known is left alone.
     */
    fun restore() {
        if (!pending) start()
    }

    /** Polled while the user is away. True once per trip: the keyboard has just been switched on. */
    fun sawSwitchedOn(enabled: Boolean): Boolean {
        if (!pending || !enabled || switchedOn) return false
        switchedOn = true
        return true
    }

    /** Home's request to come back was honoured: the intent it sent itself has arrived. */
    fun broughtBack() {
        if (pending) broughtBack = true
    }

    /** Home is in front again, which ends the trip. */
    fun finish(enabled: Boolean, selected: Boolean): Outcome {
        val wasPending = pending
        val auto = broughtBack
        pending = false
        switchedOn = false
        broughtBack = false
        if (!enabled) return Outcome(enabledVia = null, openPicker = null)
        if (!wasPending) return Outcome(EnabledVia.OTHER, openPicker = null)
        val trigger = if (auto) PickerTrigger.AUTO_RETURN else PickerTrigger.MANUAL_RETURN
        return Outcome(EnabledVia.SETUP, openPicker = trigger.takeUnless { selected })
    }

    /**
     * @param enabledVia how the keyboard came to be on, or null while it is off.
     * @param openPicker the [PickerTrigger] to open the picker with, or null to leave it closed.
     */
    data class Outcome(val enabledVia: EnabledVia?, val openPicker: String?)

    companion object {
        /** Android 15 (VANILLA_ICE_CREAM). */
        const val FIRST_SDK_WITHOUT_AUTO_RETURN = 35

        /**
         * How long Home ignores Back after it brought itself back: a Back press meant for the
         * settings screen that has just gone would otherwise close the app, Home being the
         * bottom of its task.
         */
        const val STALE_BACK_GUARD_MS = 2_500L
    }
}

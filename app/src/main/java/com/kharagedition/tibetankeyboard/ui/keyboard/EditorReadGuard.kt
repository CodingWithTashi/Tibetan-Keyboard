package com.kharagedition.tibetankeyboard.ui.keyboard

/**
 * Keeps a hung editor from freezing the keyboard.
 *
 * `InputConnection.getTextBeforeCursor` is a synchronous call into the app being typed in, made on
 * our main thread. When that app is stuck it waits out the platform's timeout on every key, and a
 * held Delete repeats the key, so the waits pile up into "Input dispatching timed out" (an ANR).
 *
 * After one read that was slow (or lost), reads are skipped for [cooldownMs] and answer null,
 * which is what the composer already treats as "the editor cannot say": the typed key still goes
 * through, only the suggestion state is dropped. The next read after the cooldown is the probe: a
 * healthy editor answers fast and the guard stays out of the way.
 *
 * Pure (the clock is passed in) so it is unit-tested.
 */
class EditorReadGuard(
    private val now: () -> Long,
    private val slowMs: Long = SLOW_MS,
    private val cooldownMs: Long = COOLDOWN_MS,
) {
    private var blockedUntil = 0L

    /** Runs [read] unless the editor was recently slow; null when skipped or the editor said null. */
    fun <T> read(read: () -> T?): T? {
        val start = now()
        if (start < blockedUntil) return null
        val result = read()
        if (now() - start >= slowMs) blockedUntil = now() + cooldownMs
        return result
    }

    /** A new editor is bound: whatever was slow before is not this one. */
    fun reset() {
        blockedUntil = 0L
    }

    companion object {
        /** A healthy editor answers in a few ms; this is well past that and well under an ANR. */
        const val SLOW_MS = 250L
        const val COOLDOWN_MS = 5_000L
    }
}

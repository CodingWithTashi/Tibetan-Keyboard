package com.kharagedition.tibetankeyboard.ui.keyboard

/**
 * Lets an in-app text field tell the Tibetan IME which script the user is
 * expected to type, so the keyboard opens on the matching layout instead of
 * the user's last global choice.
 *
 * Use case: the AI Translate **source** field. With an English source the user
 * must type Latin, so the field asks the keyboard for QWERTY; with a Tibetan
 * source it asks for the Uchen layout. Without this the keyboard kept whatever
 * language was last selected (usually Tibetan) and the user had to tap the
 * globe key every time.
 *
 * Carried over [android.view.inputmethod.EditorInfo.privateImeOptions] — a
 * private channel between this app and its own IME, scoped to the focused
 * field. It never writes the persisted keyboard-language preference, so the
 * user's choice in every other app is left untouched.
 */
object KeyboardLayoutHint {
    private const val KEY = "com.kharagedition.tibetankeyboard.forceLayout"
    private const val TIBETAN = "bo"
    private const val LATIN = "en"

    /**
     * The privateImeOptions value a translate source field should advertise.
     * Only Tibetan ("bo") needs the Uchen layout; every other source language
     * (English, Chinese, …) is entered in Latin script, so we force QWERTY.
     */
    fun privateImeOption(sourceLang: String): String {
        val layout = if (sourceLang == TIBETAN) TIBETAN else LATIN
        return "$KEY=$layout"
    }

    /**
     * Reads a focused field's privateImeOptions back into a layout decision.
     *
     * @return `true` → force the Tibetan layout, `false` → force QWERTY,
     *         `null` → no hint present, so the keyboard keeps the user's own
     *         language choice (the case for every field outside this app).
     */
    fun forcedTibetan(privateImeOptions: String?): Boolean? {
        val value = privateImeOptions
            ?.split(',')
            ?.firstOrNull { it.substringBefore('=').trim() == KEY }
            ?.substringAfter('=', "")
            ?.trim()
            ?: return null
        return when (value) {
            TIBETAN -> true
            LATIN -> false
            else -> null
        }
    }
}
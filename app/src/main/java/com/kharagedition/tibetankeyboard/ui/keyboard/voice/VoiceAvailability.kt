package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import android.text.InputType

/**
 * Whether the focused field may offer voice typing. A password is never dictated: the mic is
 * hidden there, whatever the app says.
 */
object VoiceAvailability {

    /** The two spellings Android keyboards honour in `privateImeOptions` to say "no mic here". */
    private val NO_MIC_OPTIONS = setOf("noMicrophoneKey", "nm")

    /** [inputType] and [privateImeOptions] are the focused field's `EditorInfo` values. */
    fun allowed(inputType: Int, privateImeOptions: String?): Boolean =
        !isPasswordField(inputType) && !appSwitchedMicOff(privateImeOptions)

    private fun isPasswordField(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun appSwitchedMicOff(privateImeOptions: String?): Boolean =
        privateImeOptions?.split(',')?.any { it.trim() in NO_MIC_OPTIONS } == true
}

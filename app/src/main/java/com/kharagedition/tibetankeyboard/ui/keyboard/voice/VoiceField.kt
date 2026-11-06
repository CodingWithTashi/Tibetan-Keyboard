package com.kharagedition.tibetankeyboard.ui.keyboard.voice

/**
 * The text field a dictation started in. A transcript is committed only if the keyboard is still
 * on that field: the user may have switched apps, or tapped another field, while it was written.
 */
class VoiceField private constructor(
    private val packageName: String,
    private val fieldId: Int,
) {
    fun matches(other: VoiceField?): Boolean =
        other != null && other.packageName == packageName && other.fieldId == fieldId

    companion object {
        /**
         * [fieldId] is `EditorInfo.fieldId`, which is 0 when the app never set it, so then the
         * package alone tells fields apart. Null when the app is unknown: such a field is never
         * matched, so its dictation is dropped rather than risk typing into the wrong place.
         */
        fun of(packageName: String?, fieldId: Int): VoiceField? =
            if (packageName.isNullOrEmpty()) null else VoiceField(packageName, fieldId)
    }
}

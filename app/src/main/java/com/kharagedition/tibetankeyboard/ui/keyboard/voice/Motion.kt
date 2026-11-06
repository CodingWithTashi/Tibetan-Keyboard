package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import android.content.Context
import android.provider.Settings

/** Whether the user wants animation. "Remove animations" (animator scale 0) holds the panel still. */
internal object Motion {
    fun enabled(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
}

package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/**
 * Whether voice typing has worked for this user once. Until it has, the mic in the keyboard bar
 * wears a NEW dot, and the first result says "Your first spoken message" instead of the streak.
 */
object VoiceIntro {
    private const val KEY_SEEN = "voice_intro_seen"

    fun isSeen(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_SEEN, false)

    fun markSeen(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit { putBoolean(KEY_SEEN, true) }
    }
}

package com.kharagedition.tibetankeyboard.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Single source of truth for the keyboard preference keys/values.
 *
 * IMPORTANT: these live in [PreferenceManager.getDefaultSharedPreferences], the
 * exact store the IME service (TibetanKeyboard) reads at runtime — so changing
 * them here updates the live keyboard. Do not rename the keys/values.
 */
object SettingsPrefs {
    const val KEY_COLOR = "colors"
    const val KEY_STYLE = "keyboard_style"
    const val KEY_VIBRATE = "vibrate"
    const val KEY_SOUND = "sound"
    const val KEY_NOTIFICATION = "event_notification"

    const val COLOR_BROWN = "#FF704C04"
    const val COLOR_BLACK = "#FF000000"
    const val COLOR_GREEN = "#FF007500"

    const val STYLE_CLASSIC = "classic"
    const val STYLE_MODERN = "modern"
    const val STYLE_BORDERLESS = "borderless"

    val colorOptions = listOf(
        PrefOption("Brown", "Warm monk-robe brown", COLOR_BROWN),
        PrefOption("Black", "Deep neutral dark", COLOR_BLACK),
        PrefOption("Green", "Forest green", COLOR_GREEN),
    )

    // Mirrors the design: A · Flat modern, B · Raised key caps, C · Borderless.
    val styleOptions = listOf(
        PrefOption("Flat modern", "Subtle flat keys (Google style)", STYLE_MODERN),
        PrefOption("Raised key caps", "3D gradient key caps", STYLE_CLASSIC),
        PrefOption("Borderless", "Minimal — glyphs only, no boxes", STYLE_BORDERLESS),
    )

    fun colorLabel(value: String) = colorOptions.firstOrNull { it.value == value }?.label ?: "Brown"
    fun styleLabel(value: String) = styleOptions.firstOrNull { it.value == value }?.label ?: "Raised key caps"

    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun read(context: Context): SettingsState {
        val p = prefs(context)
        return SettingsState(
            color = p.getString(KEY_COLOR, COLOR_BROWN) ?: COLOR_BROWN,
            style = p.getString(KEY_STYLE, STYLE_CLASSIC) ?: STYLE_CLASSIC,
            vibrate = p.getBoolean(KEY_VIBRATE, false),
            sound = p.getBoolean(KEY_SOUND, true),
            eventNotification = p.getBoolean(KEY_NOTIFICATION, true),
        )
    }

    fun putString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }
}

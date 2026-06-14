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
    const val KEY_AI_MODEL = "ai_model"

    const val COLOR_BROWN = "#FF704C04"
    const val COLOR_BLACK = "#FF000000"
    const val COLOR_GREEN = "#FF007500"

    const val STYLE_CLASSIC = "classic"
    const val STYLE_MODERN = "modern"
    const val STYLE_BORDERLESS = "borderless"

    // Free-tier defaults. Everything else is PRO-only.
    const val DEFAULT_COLOR = COLOR_BROWN
    const val DEFAULT_STYLE = STYLE_BORDERLESS

    // Claude models the user can switch between (must match the backend allow-list).
    const val MODEL_HAIKU = "claude-haiku-4-5"
    const val MODEL_SONNET = "claude-sonnet-4-6"
    const val DEFAULT_MODEL = MODEL_HAIKU

    // Brown is free; black & green are PRO.
    val colorOptions = listOf(
        PrefOption("Brown", "Warm monk-robe brown", COLOR_BROWN, premium = false),
        PrefOption("Black", "Deep neutral dark", COLOR_BLACK, premium = true),
        PrefOption("Green", "Forest green", COLOR_GREEN, premium = true),
    )

    // Borderless is free; the two raised/flat styles are PRO.
    val styleOptions = listOf(
        PrefOption("Borderless", "Minimal — glyphs only, no boxes", STYLE_BORDERLESS, premium = false),
        PrefOption("Flat modern", "Subtle flat keys (Google style)", STYLE_MODERN, premium = true),
        PrefOption("Raised key caps", "3D gradient key caps", STYLE_CLASSIC, premium = true),
    )

    val modelOptions = listOf(
        PrefOption("Haiku 4.5", "Fast & efficient", MODEL_HAIKU, premium = false),
        PrefOption("Sonnet 4.6", "Most capable", MODEL_SONNET, premium = false),
    )

    fun colorLabel(value: String) = colorOptions.firstOrNull { it.value == value }?.label ?: "Brown"
    fun styleLabel(value: String) = styleOptions.firstOrNull { it.value == value }?.label ?: "Borderless"
    fun modelLabel(value: String) = modelOptions.firstOrNull { it.value == value }?.label ?: "Haiku 4.5"

    /** True when [value] is a PRO-only keyboard colour. */
    fun isColorPremium(value: String) = colorOptions.firstOrNull { it.value == value }?.premium == true

    /** True when [value] is a PRO-only keyboard layout. */
    fun isStylePremium(value: String) = styleOptions.firstOrNull { it.value == value }?.premium == true

    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun read(context: Context): SettingsState {
        val p = prefs(context)
        return SettingsState(
            color = p.getString(KEY_COLOR, DEFAULT_COLOR) ?: DEFAULT_COLOR,
            style = p.getString(KEY_STYLE, DEFAULT_STYLE) ?: DEFAULT_STYLE,
            vibrate = p.getBoolean(KEY_VIBRATE, false),
            sound = p.getBoolean(KEY_SOUND, true),
            eventNotification = p.getBoolean(KEY_NOTIFICATION, true),
        )
    }

    fun readModel(context: Context): String =
        prefs(context).getString(KEY_AI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun putString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }
}

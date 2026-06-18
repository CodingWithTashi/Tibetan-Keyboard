package com.kharagedition.tibetankeyboard.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.kharagedition.tibetankeyboard.BuildConfig

/**
 * Single source of truth for product analytics (Firebase Analytics / GA4).
 *
 * Every custom event the app reports lives here as a typed method, so call sites stay clean
 * (`AppAnalytics.logChatMessageSent(model)`) and the full event taxonomy is visible in one file.
 *
 * **Release-only:** [init] only wires a real [FirebaseAnalytics] instance for release builds. In
 * debug builds the backing instance stays null, so every `log*` call is a no-op and no event is
 * ever sent. This is the single gate — there is no per-call-site `if (BuildConfig.DEBUG)`.
 *
 * Not to be confused with [com.kharagedition.tibetankeyboard.data.repository.UserRepository.trackUserEvent],
 * which writes per-user usage docs to Firestore for backend limits. This object feeds the
 * Firebase console dashboards ("which feature is most used").
 */
object AppAnalytics {

    /** Null in debug builds → all logging is a no-op. Set once in [init] for release builds. */
    private var analytics: FirebaseAnalytics? = null

    /** Wire up Firebase Analytics. Call once from Application.onCreate. No-op in debug builds. */
    fun init(context: Context) {
        if (BuildConfig.DEBUG) return
        analytics = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    /** Associate following events with the signed-in user (GA4 user id). Cleared on logout. */
    fun setUser(userId: String?) {
        analytics?.setUserId(userId)
    }

    /**
     * Record keyboard-setup completion as user properties so users can be segmented by whether
     * they ever enabled the IME / made it their default. Idempotent — only changes are reported.
     */
    fun setKeyboardSetup(enabled: Boolean, isDefault: Boolean) {
        analytics?.setUserProperty(USER_PROP_KB_ENABLED, enabled.toString())
        analytics?.setUserProperty(USER_PROP_KB_DEFAULT, isDefault.toString())
    }

    // ----------------------------------------------------------------------------------------
    // Navigation / Home hub
    // ----------------------------------------------------------------------------------------

    /** A tap on a Home-screen tile. [action] is one of [HomeAction]. */
    fun logHomeAction(action: String) = log(EVENT_HOME_ACTION, PARAM_ACTION to action)

    // ----------------------------------------------------------------------------------------
    // Auth / Login
    // ----------------------------------------------------------------------------------------

    /** A successful sign-in. New users are reported as `sign_up`, returning users as `login`. */
    fun logLogin(isNewUser: Boolean, method: String = METHOD_GOOGLE) {
        val event = if (isNewUser) FirebaseAnalytics.Event.SIGN_UP else FirebaseAnalytics.Event.LOGIN
        log(event, FirebaseAnalytics.Param.METHOD to method)
    }

    fun logLoginFailed(reason: String, method: String = METHOD_GOOGLE) {
        log(EVENT_LOGIN_FAILED, FirebaseAnalytics.Param.METHOD to method, PARAM_REASON to reason)
    }

    fun logLogout() = log(EVENT_LOGOUT)

    // ----------------------------------------------------------------------------------------
    // Chat
    // ----------------------------------------------------------------------------------------

    fun logChatOpened() = log(EVENT_CHAT_OPENED)

    fun logChatMessageSent(model: String) = log(EVENT_CHAT_MESSAGE_SENT, PARAM_MODEL to model)

    fun logChatResponseReceived(model: String, success: Boolean) =
        log(EVENT_CHAT_RESPONSE_RECEIVED, PARAM_MODEL to model, PARAM_SUCCESS to success)

    fun logChatCleared() = log(EVENT_CHAT_CLEARED)

    fun logChatModelChanged(model: String) = log(EVENT_CHAT_MODEL_CHANGED, PARAM_MODEL to model)

    // ----------------------------------------------------------------------------------------
    // Translate
    // ----------------------------------------------------------------------------------------

    fun logTranslateOpened() = log(EVENT_TRANSLATE_OPENED)

    fun logTranslatePerformed(sourceLang: String, targetLang: String, engine: String, success: Boolean) =
        log(
            EVENT_TRANSLATE_PERFORMED,
            PARAM_SOURCE_LANG to sourceLang,
            PARAM_TARGET_LANG to targetLang,
            PARAM_ENGINE to engine,
            PARAM_SUCCESS to success,
        )

    fun logTranslateLanguagesSwapped() = log(EVENT_TRANSLATE_SWAPPED)

    fun logTranslateEngineChanged(engine: String) = log(EVENT_TRANSLATE_ENGINE_CHANGED, PARAM_ENGINE to engine)

    // ----------------------------------------------------------------------------------------
    // Subscription / Paywall
    // ----------------------------------------------------------------------------------------

    /** An "unlock PRO" intent, before any login/paywall is shown. [source] is one of [UpgradeSource]. */
    fun logUpgradeClicked(source: String) = log(EVENT_UPGRADE_CLICKED, PARAM_SOURCE to source)

    fun logPaywallViewed() = log(EVENT_PAYWALL_VIEWED)

    fun logPurchaseStarted() = log(EVENT_PURCHASE_STARTED)

    fun logPurchaseCompleted() = log(EVENT_PURCHASE_COMPLETED)

    fun logPurchaseFailed(reason: String) = log(EVENT_PURCHASE_FAILED, PARAM_REASON to reason)

    fun logPurchaseRestored() = log(EVENT_PURCHASE_RESTORED)

    // ----------------------------------------------------------------------------------------
    // Keyboard setup funnel (does the user ever enable / default the IME?)
    // ----------------------------------------------------------------------------------------

    fun logKeyboardEnableClicked() = log(EVENT_KEYBOARD_ENABLE_CLICKED)

    fun logKeyboardPickerOpened() = log(EVENT_KEYBOARD_PICKER_OPENED)

    // ----------------------------------------------------------------------------------------
    // Keyboard usage (the IME itself)
    // ----------------------------------------------------------------------------------------

    /** The keyboard surfaced in some app. The core "is the keyboard being used" signal. */
    fun logKeyboardShown() = log(EVENT_KEYBOARD_SHOWN)

    /** User toggled the typing language. [language] is one of [KeyboardLanguage]. */
    fun logKeyboardLanguageSwitched(language: String) =
        log(EVENT_KEYBOARD_LANGUAGE_SWITCHED, PARAM_LANGUAGE to language)

    fun logKeyboardEmojiOpened() = log(EVENT_KEYBOARD_EMOJI_OPENED)

    /** User accepted a Botok autocomplete suggestion. */
    fun logKeyboardSuggestionSelected() = log(EVENT_KEYBOARD_SUGGESTION_SELECTED)

    // ----------------------------------------------------------------------------------------
    // Keyboard AI (the IME — grammar / translate / rephrase from the keyboard toolbar)
    // ----------------------------------------------------------------------------------------

    /** An AI feature was invoked from the keyboard. [feature] is one of [KeyboardFeature]. */
    fun logKeyboardAiUsed(feature: String) = log(EVENT_KEYBOARD_AI_USED, PARAM_FEATURE to feature)

    /** The user accepted/applied the AI result back into the text field. [feature] is one of [KeyboardFeature]. */
    fun logKeyboardAiApplied(feature: String) = log(EVENT_KEYBOARD_AI_APPLIED, PARAM_FEATURE to feature)

    // ----------------------------------------------------------------------------------------
    // Settings
    // ----------------------------------------------------------------------------------------

    /** A settings value changed. [setting] is one of [Setting]; [value] is the new value. */
    fun logSettingChanged(setting: String, value: String) =
        log(EVENT_SETTING_CHANGED, PARAM_SETTING to setting, PARAM_VALUE to value)

    // ----------------------------------------------------------------------------------------
    // Push notifications (FCM)
    // ----------------------------------------------------------------------------------------

    fun logNotificationReceived(type: String) = log(EVENT_NOTIFICATION_RECEIVED, PARAM_TYPE to type)

    fun logNotificationOpened() = log(EVENT_NOTIFICATION_OPENED)

    // ----------------------------------------------------------------------------------------
    // Internals
    // ----------------------------------------------------------------------------------------

    /** Build a [Bundle] from the given params and forward to Firebase. No-op when [analytics] is null. */
    private fun log(event: String, vararg params: Pair<String, Any?>) {
        val fa = analytics ?: return
        val bundle = Bundle()
        for ((key, value) in params) {
            when (value) {
                null -> {}
                is String -> bundle.putString(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Float -> bundle.putDouble(key, value.toDouble())
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putString(key, value.toString())
                else -> bundle.putString(key, value.toString())
            }
        }
        fa.logEvent(event, bundle)
    }

    // Event names (snake_case, GA4 convention). Standard events reuse FirebaseAnalytics.Event.*
    private const val EVENT_HOME_ACTION = "home_action"

    private const val EVENT_LOGIN_FAILED = "login_failed"
    private const val EVENT_LOGOUT = "logout"

    private const val EVENT_CHAT_OPENED = "chat_opened"
    private const val EVENT_CHAT_MESSAGE_SENT = "chat_message_sent"
    private const val EVENT_CHAT_RESPONSE_RECEIVED = "chat_response_received"
    private const val EVENT_CHAT_CLEARED = "chat_cleared"
    private const val EVENT_CHAT_MODEL_CHANGED = "chat_model_changed"

    private const val EVENT_TRANSLATE_OPENED = "translate_opened"
    private const val EVENT_TRANSLATE_PERFORMED = "translate_performed"
    private const val EVENT_TRANSLATE_SWAPPED = "translate_languages_swapped"
    private const val EVENT_TRANSLATE_ENGINE_CHANGED = "translate_engine_changed"

    private const val EVENT_UPGRADE_CLICKED = "upgrade_clicked"
    private const val EVENT_PAYWALL_VIEWED = "paywall_viewed"
    private const val EVENT_PURCHASE_STARTED = "purchase_started"
    private const val EVENT_PURCHASE_COMPLETED = "purchase_completed"
    private const val EVENT_PURCHASE_FAILED = "purchase_failed"
    private const val EVENT_PURCHASE_RESTORED = "purchase_restored"

    private const val EVENT_KEYBOARD_ENABLE_CLICKED = "keyboard_enable_clicked"
    private const val EVENT_KEYBOARD_PICKER_OPENED = "keyboard_picker_opened"
    private const val EVENT_KEYBOARD_SHOWN = "keyboard_shown"
    private const val EVENT_KEYBOARD_LANGUAGE_SWITCHED = "keyboard_language_switched"
    private const val EVENT_KEYBOARD_EMOJI_OPENED = "keyboard_emoji_opened"
    private const val EVENT_KEYBOARD_SUGGESTION_SELECTED = "keyboard_suggestion_selected"
    private const val EVENT_KEYBOARD_AI_USED = "keyboard_ai_used"
    private const val EVENT_KEYBOARD_AI_APPLIED = "keyboard_ai_applied"

    private const val EVENT_SETTING_CHANGED = "setting_changed"

    private const val EVENT_NOTIFICATION_RECEIVED = "notification_received"
    private const val EVENT_NOTIFICATION_OPENED = "notification_opened"

    // Param keys
    private const val PARAM_ACTION = "action"
    private const val PARAM_REASON = "reason"
    private const val PARAM_MODEL = "model"
    private const val PARAM_SUCCESS = "success"
    private const val PARAM_SOURCE_LANG = "source_lang"
    private const val PARAM_TARGET_LANG = "target_lang"
    private const val PARAM_ENGINE = "engine"
    private const val PARAM_FEATURE = "feature"
    private const val PARAM_LANGUAGE = "language"
    private const val PARAM_SETTING = "setting"
    private const val PARAM_VALUE = "value"
    private const val PARAM_SOURCE = "source"
    private const val PARAM_TYPE = "type"

    // User properties
    private const val USER_PROP_KB_ENABLED = "kb_enabled"
    private const val USER_PROP_KB_DEFAULT = "kb_default"

    // Param values
    private const val METHOD_GOOGLE = "google"

    /** Stable feature labels for [logKeyboardAiUsed] / [logKeyboardAiApplied]. */
    object KeyboardFeature {
        const val GRAMMAR = "grammar"
        const val TRANSLATE = "translate"
        const val REPHRASE = "rephrase"
    }

    /** Stable language labels for [logKeyboardLanguageSwitched]. */
    object KeyboardLanguage {
        const val TIBETAN = "tibetan"
        const val ENGLISH = "english"
    }

    /** Stable action labels for [logHomeAction]. */
    object HomeAction {
        const val ENABLE_KEYBOARD = "enable_keyboard"
        const val PICK_INPUT_METHOD = "pick_input_method"
        const val CHAT = "chat"
        const val TRANSLATE = "translate"
        const val SETTINGS = "settings"
        const val SHARE = "share"
        const val RATE = "rate"
        const val ABOUT = "about"
        const val UPGRADE = "upgrade"
    }

    /** Stable setting labels for [logSettingChanged]. */
    object Setting {
        const val COLOR = "color"
        const val STYLE = "style"
        const val VIBRATE = "vibrate"
        const val SOUND = "sound"
        const val NOTIFICATION = "notification"
    }

    /** Stable source labels for [logUpgradeClicked] (where the upgrade intent originated). */
    object UpgradeSource {
        const val HOME = "home"
        const val HOME_FEATURE_GATE = "home_feature_gate"
        const val CHAT = "chat"
        const val TRANSLATE = "translate"
        const val SETTINGS = "settings"
        const val KEYBOARD = "keyboard"
        const val UNKNOWN = "unknown"
    }
}

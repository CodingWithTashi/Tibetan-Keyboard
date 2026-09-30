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

    /**
     * The user's Google Play country (from Play Billing), which can differ from the IP country GA4
     * reports — a Bhutanese IP with an Indian Play account can still pay through Play.
     */
    fun setPlayCountry(countryCode: String) {
        analytics?.setUserProperty(USER_PROP_PLAY_COUNTRY, countryCode)
    }

    /** The side of the card checkout paywall test, so any report can be split by it. */
    fun setWebPaywallVariant(variant: String) {
        analytics?.setUserProperty(USER_PROP_WEB_PAYWALL_VARIANT, variant)
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

    /** A PRO lock was shown. Pairs with [logUpgradeClicked] to separate "never saw it" from "passed". */
    fun logFeatureGateShown(source: String) = log(EVENT_FEATURE_GATE_SHOWN, PARAM_SOURCE to source)

    /** An "unlock PRO" intent, before any login/paywall is shown. [source] is one of [UpgradeSource]. */
    fun logUpgradeClicked(source: String) = log(EVENT_UPGRADE_CLICKED, PARAM_SOURCE to source)

    // Every event below carries the [UpgradeSource] that opened the paywall, so a sale can be
    // attributed to the gate that caused it.

    /**
     * [placement] is the RevenueCat placement the paywall was resolved for, [offering] the offering
     * shown, and [paywallType] one of [PaywallType] — together they tell which variant converted.
     */
    fun logPaywallViewed(
        source: String,
        placement: String? = null,
        offering: String? = null,
        paywallType: String? = null,
        variant: String? = null,
    ) = log(
        EVENT_PAYWALL_VIEWED,
        PARAM_SOURCE to source,
        PARAM_PLACEMENT to placement,
        PARAM_OFFERING to offering,
        PARAM_PAYWALL_TYPE to paywallType,
        PARAM_VARIANT to variant,
    )

    /** The first-run / day-3 upsell opened by itself (not from a tap). */
    fun logOnboardingPaywallShown(source: String) = log(EVENT_ONBOARDING_PAYWALL_SHOWN, PARAM_SOURCE to source)

    /** Where Play can't sell, the user was sent to card checkout instead. [reason] says why. */
    fun logBillingUnavailable(reason: String) = log(EVENT_BILLING_UNAVAILABLE, PARAM_REASON to reason)

    /** [variant] is the side of the card checkout paywall test, a `WebPaywallVariant` label. */
    fun logWebCheckoutOpened(source: String, plan: String, variant: String?) =
        log(EVENT_WEB_CHECKOUT_OPENED, PARAM_SOURCE to source, PARAM_PLAN to plan, PARAM_VARIANT to variant)

    fun logWebCheckoutCompleted(source: String, plan: String, variant: String?) =
        log(EVENT_WEB_CHECKOUT_COMPLETED, PARAM_SOURCE to source, PARAM_PLAN to plan, PARAM_VARIANT to variant)

    fun logPurchaseStarted(source: String, plan: String) =
        log(EVENT_PURCHASE_STARTED, PARAM_SOURCE to source, PARAM_PLAN to plan)

    fun logPurchaseCompleted(source: String, plan: String, price: Double?, currency: String?) =
        log(
            EVENT_PURCHASE_COMPLETED,
            PARAM_SOURCE to source,
            PARAM_PLAN to plan,
            FirebaseAnalytics.Param.VALUE to price,
            FirebaseAnalytics.Param.CURRENCY to currency,
        )

    /** [code] is a stable `PurchasesErrorCode`; [reason] is localized and unbounded cardinality. */
    fun logPurchaseFailed(source: String, plan: String, code: String, reason: String) =
        log(
            EVENT_PURCHASE_FAILED,
            PARAM_SOURCE to source,
            PARAM_PLAN to plan,
            PARAM_CODE to code,
            PARAM_REASON to reason,
        )

    /** The user dismissed Play's purchase sheet — the largest silent drop in the funnel. */
    fun logPurchaseCancelled(source: String, plan: String) =
        log(EVENT_PURCHASE_CANCELLED, PARAM_SOURCE to source, PARAM_PLAN to plan)

    fun logPurchaseRestored() = log(EVENT_PURCHASE_RESTORED)

    /** A subscriber opened Manage subscription (overview, restore, cancel flow). */
    fun logManageSubscriptionOpened() = log(EVENT_MANAGE_SUBSCRIPTION_OPENED)

    fun logRestoreFailed(code: String, reason: String) =
        log(EVENT_RESTORE_FAILED, PARAM_CODE to code, PARAM_REASON to reason)

    // Cancellation flow: reason → (retention offer) → hand-off to the store.

    fun logCancelStarted() = log(EVENT_CANCEL_STARTED)

    fun logCancelReason(reason: String) = log(EVENT_CANCEL_REASON, PARAM_REASON to reason)

    fun logRetentionOfferShown() = log(EVENT_RETENTION_OFFER_SHOWN)

    fun logRetentionOfferAccepted() = log(EVENT_RETENTION_OFFER_ACCEPTED)

    fun logRetentionOfferDeclined() = log(EVENT_RETENTION_OFFER_DECLINED)

    fun logRetentionOfferFailed(code: String) = log(EVENT_RETENTION_OFFER_FAILED, PARAM_CODE to code)

    /** The user left for Google Play / the billing portal to finish cancelling. */
    fun logCancelHandoff(reason: String?) = log(EVENT_CANCEL_HANDOFF, PARAM_REASON to reason)

    /** A cancelled subscriber left for the store to turn renewal back on. */
    fun logResubscribeOpened() = log(EVENT_RESUBSCRIBE_OPENED)

    /** The user backed out of cancelling; [step] is where they stopped. */
    fun logCancelAbandoned(step: String) = log(EVENT_CANCEL_ABANDONED, PARAM_STEP to step)

    fun logPaywallDismissed(source: String) = log(EVENT_PAYWALL_DISMISSED, PARAM_SOURCE to source)

    fun logPaywallPlanSelected(source: String, plan: String) =
        log(EVENT_PAYWALL_PLAN_SELECTED, PARAM_SOURCE to source, PARAM_PLAN to plan)

    // ----------------------------------------------------------------------------------------
    // Keyboard setup funnel (does the user ever enable / default the IME?)
    // ----------------------------------------------------------------------------------------

    fun logKeyboardEnableClicked() = log(EVENT_KEYBOARD_ENABLE_CLICKED)

    fun logKeyboardPickerOpened() = log(EVENT_KEYBOARD_PICKER_OPENED)

    /** Keyboard enabled AND selected. Once per install — the funnel's missing endpoint. */
    fun logKeyboardSetupCompleted() = log(EVENT_KEYBOARD_SETUP_COMPLETED)

    /** Typed in the in-app try-it field — first proof the keyboard works. Reports script, not text. */
    fun logKeyboardTryoutTyped(language: String) =
        log(EVENT_KEYBOARD_TRYOUT_TYPED, PARAM_LANGUAGE to language)

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

    /** A free user used up today's free suggestions; the strip switched to locked chips. */
    fun logSuggestionQuotaExhausted(limit: Int) = log(EVENT_SUGGESTION_QUOTA_EXHAUSTED, PARAM_VALUE to limit.toString())

    // ----------------------------------------------------------------------------------------
    // Keyboard AI (the IME — grammar / translate / rephrase from the keyboard toolbar)
    // ----------------------------------------------------------------------------------------

    /** An AI feature was invoked from the keyboard. [feature] is one of [KeyboardFeature]. */
    fun logKeyboardAiUsed(feature: String) = log(EVENT_KEYBOARD_AI_USED, PARAM_FEATURE to feature)

    /** The user accepted/applied the AI result back into the text field. [feature] is one of [KeyboardFeature]. */
    fun logKeyboardAiApplied(feature: String) = log(EVENT_KEYBOARD_AI_APPLIED, PARAM_FEATURE to feature)

    // ----------------------------------------------------------------------------------------
    // Journey (typing streak & insights) — events carry ONLY counts, never typed content
    // ----------------------------------------------------------------------------------------

    /** The Journey screen was opened. [source] is one of [JourneySource]. */
    fun logJourneyOpened(source: String) = log(EVENT_JOURNEY_OPENED, PARAM_SOURCE to source)

    /** The user's typing streak crossed a milestone (3/7/14/30/60/108/365 days). */
    fun logStreakMilestone(days: Int) = log(EVENT_STREAK_MILESTONE, PARAM_DAYS to days)

    /** The numbers-only community comparison was switched on/off on the Journey screen. */
    fun logJourneySyncToggled(enabled: Boolean) =
        log(EVENT_JOURNEY_SYNC_TOGGLED, PARAM_VALUE to enabled.toString())

    /** The user shared their streak (the Journey screen's share action). */
    fun logJourneyShared(days: Int) = log(EVENT_JOURNEY_SHARED, PARAM_DAYS to days)

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

    private const val EVENT_FEATURE_GATE_SHOWN = "feature_gate_shown"
    private const val EVENT_UPGRADE_CLICKED = "upgrade_clicked"
    private const val EVENT_PAYWALL_VIEWED = "paywall_viewed"
    private const val EVENT_PAYWALL_DISMISSED = "paywall_dismissed"
    private const val EVENT_PAYWALL_PLAN_SELECTED = "paywall_plan_selected"
    private const val EVENT_PURCHASE_STARTED = "purchase_started"
    private const val EVENT_PURCHASE_COMPLETED = "purchase_completed"
    private const val EVENT_PURCHASE_FAILED = "purchase_failed"
    private const val EVENT_PURCHASE_CANCELLED = "purchase_cancelled"
    private const val EVENT_PURCHASE_RESTORED = "purchase_restored"
    private const val EVENT_MANAGE_SUBSCRIPTION_OPENED = "manage_subscription_opened"
    private const val EVENT_RESTORE_FAILED = "restore_failed"
    private const val EVENT_CANCEL_STARTED = "subscription_cancel_started"
    private const val EVENT_CANCEL_REASON = "subscription_cancel_reason"
    private const val EVENT_RETENTION_OFFER_SHOWN = "retention_offer_shown"
    private const val EVENT_RETENTION_OFFER_ACCEPTED = "retention_offer_accepted"
    private const val EVENT_RETENTION_OFFER_DECLINED = "retention_offer_declined"
    private const val EVENT_RETENTION_OFFER_FAILED = "retention_offer_failed"
    private const val EVENT_CANCEL_HANDOFF = "subscription_cancel_handoff"
    private const val EVENT_CANCEL_ABANDONED = "subscription_cancel_abandoned"
    private const val EVENT_RESUBSCRIBE_OPENED = "subscription_resubscribe_opened"
    private const val EVENT_ONBOARDING_PAYWALL_SHOWN = "onboarding_paywall_shown"
    private const val EVENT_BILLING_UNAVAILABLE = "billing_unavailable"
    private const val EVENT_WEB_CHECKOUT_OPENED = "web_checkout_opened"
    private const val EVENT_WEB_CHECKOUT_COMPLETED = "web_checkout_completed"

    private const val EVENT_KEYBOARD_ENABLE_CLICKED = "keyboard_enable_clicked"
    private const val EVENT_KEYBOARD_PICKER_OPENED = "keyboard_picker_opened"
    private const val EVENT_KEYBOARD_SETUP_COMPLETED = "keyboard_setup_completed"
    private const val EVENT_KEYBOARD_TRYOUT_TYPED = "keyboard_tryout_typed"
    private const val EVENT_KEYBOARD_SHOWN = "keyboard_shown"
    private const val EVENT_KEYBOARD_LANGUAGE_SWITCHED = "keyboard_language_switched"
    private const val EVENT_KEYBOARD_EMOJI_OPENED = "keyboard_emoji_opened"
    private const val EVENT_KEYBOARD_SUGGESTION_SELECTED = "keyboard_suggestion_selected"
    private const val EVENT_SUGGESTION_QUOTA_EXHAUSTED = "suggestion_quota_exhausted"
    private const val EVENT_KEYBOARD_AI_USED = "keyboard_ai_used"
    private const val EVENT_KEYBOARD_AI_APPLIED = "keyboard_ai_applied"

    private const val EVENT_JOURNEY_OPENED = "journey_opened"
    private const val EVENT_STREAK_MILESTONE = "streak_milestone"
    private const val EVENT_JOURNEY_SYNC_TOGGLED = "journey_sync_toggled"
    private const val EVENT_JOURNEY_SHARED = "journey_shared"

    private const val EVENT_SETTING_CHANGED = "setting_changed"

    private const val EVENT_NOTIFICATION_RECEIVED = "notification_received"
    private const val EVENT_NOTIFICATION_OPENED = "notification_opened"

    // Param keys
    private const val PARAM_ACTION = "action"
    private const val PARAM_REASON = "reason"
    private const val PARAM_STEP = "step"
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
    private const val PARAM_DAYS = "days"
    private const val PARAM_PLAN = "plan"
    private const val PARAM_CODE = "code"
    private const val PARAM_PLACEMENT = "placement"
    private const val PARAM_OFFERING = "offering"
    private const val PARAM_PAYWALL_TYPE = "paywall_type"
    private const val PARAM_VARIANT = "variant"

    // User properties
    private const val USER_PROP_KB_ENABLED = "kb_enabled"
    private const val USER_PROP_KB_DEFAULT = "kb_default"
    private const val USER_PROP_PLAY_COUNTRY = "play_country"
    private const val USER_PROP_WEB_PAYWALL_VARIANT = "web_paywall_variant"

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
        const val JOURNEY = "journey"
        const val SIGN_IN = "sign_in"
        const val ACCOUNT = "account"
    }

    /** Stable source labels for [logJourneyOpened] (where the Journey screen was opened from). */
    object JourneySource {
        const val KEYBOARD = "keyboard"
        const val HOME = "home"
        const val NOTIFICATION = "notification"
    }

    /** Stable setting labels for [logSettingChanged]. */
    object Setting {
        const val COLOR = "color"
        const val STYLE = "style"
        const val VIBRATE = "vibrate"
        const val SOUND = "sound"
        const val NOTIFICATION = "notification"
        const val STREAK_REMINDER = "streak_reminder"
    }

    /** Stable plan labels for the purchase funnel events. Mirrors the RevenueCat package types. */
    object Plan {
        const val MONTHLY = "monthly"
        const val ANNUAL = "annual"
        const val LIFETIME = "lifetime"
        const val UNKNOWN = "unknown"
    }

    /** Stable source labels for [logUpgradeClicked] (where the upgrade intent originated). */
    object UpgradeSource {
        const val HOME = "home"
        const val HOME_FEATURE_GATE = "home_feature_gate"
        const val CHAT = "chat"
        const val TRANSLATE = "translate"
        const val SETTINGS = "settings"
        const val KEYBOARD = "keyboard"
        /** A locked suggestion chip, after the free daily suggestions ran out. */
        const val KEYBOARD_SUGGESTIONS = "keyboard_suggestions"
        const val JOURNEY = "journey"
        /** Opened by itself right after keyboard setup; see `OnboardingPaywallPolicy`. */
        const val ONBOARDING = "onboarding"
        const val ONBOARDING_DAY3 = "onboarding_day3"
        /** The one-time showing to people who set the keyboard up before this paywall existed. */
        const val ONBOARDING_EXISTING = "onboarding_existing"
        const val UNKNOWN = "unknown"

        val ALL = listOf(
            HOME, HOME_FEATURE_GATE, CHAT, TRANSLATE, SETTINGS, KEYBOARD, KEYBOARD_SUGGESTIONS,
            JOURNEY, ONBOARDING, ONBOARDING_DAY3, ONBOARDING_EXISTING, UNKNOWN,
        )
    }

    /** Which paywall rendered, for [logPaywallViewed]. */
    object PaywallType {
        /** Designed in the RevenueCat dashboard. */
        const val DASHBOARD = "dashboard"
        /** Our own Compose screen — the fallback when no dashboard paywall is attached. */
        const val CUSTOM = "custom"
        /** Card checkout, only where Google Play can't sell (Bhutan). */
        const val WEB = "web"
    }
}

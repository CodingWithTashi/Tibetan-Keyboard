package com.kharagedition.tibetankeyboard.ui.keyboard

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics

interface AIKeyboardInterface {
    fun getCurrentText(): String
    fun onGrammarReplace(originalText: String, correctedText: String)
    fun onTranslateReplace(originalText: String, translatedText: String)
    fun onRephraseReplace(originalText: String, rephrasedText: String)
    fun onAICancel()

    /**
     * The user tapped [word] in the suggestion strip. [matched] is the end of the typed prefix
     * the word completes (see `SuggestionEngine.suggest`): that, and only that, gets replaced.
     */
    fun onSuggestionSelected(word: String, matched: String)

    /** Open the AI chat screen (PRO users). */
    fun onOpenChat()

    /**
     * Route a free user to the paywall. [source] is the [AppAnalytics.UpgradeSource] of the
     * control that was tapped, so the sale can be attributed to it.
     */
    fun onUnlockPro(source: String = AppAnalytics.UpgradeSource.KEYBOARD)

    /** The bar's mic was tapped: open the voice panel and start listening (the service owns the session). */
    fun onVoiceRequested()

    /** The voice panel was closed from its keyboard button. Any recording is thrown away. */
    fun onVoiceClosed()

    /** The panel's "Allow microphone" button: send the user to the permission request. */
    fun onVoiceAllowMicrophone()

    /** The panel's "Open settings" button, after the microphone was refused for good. */
    fun onVoiceOpenSettings()

    /** The panel's delete button: the same as the keyboard's backspace. */
    fun onVoiceBackspace()
}
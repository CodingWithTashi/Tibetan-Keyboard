package com.kharagedition.tibetankeyboard.ui.keyboard

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics

interface AIKeyboardInterface {
    fun getCurrentText(): String
    fun onGrammarReplace(originalText: String, correctedText: String)
    fun onTranslateReplace(originalText: String, translatedText: String)
    fun onRephraseReplace(originalText: String, rephrasedText: String)
    fun onAICancel()
    fun onSuggestionSelected(word: String)

    /** Open the AI chat screen (PRO users). */
    fun onOpenChat()

    /**
     * Route a free user to the paywall. [source] is the [AppAnalytics.UpgradeSource] of the
     * control that was tapped, so the sale can be attributed to it.
     */
    fun onUnlockPro(source: String = AppAnalytics.UpgradeSource.KEYBOARD)

    /** Open the Tibetan Journey (streak & typing insights) screen. */
    fun onOpenJourney()
}
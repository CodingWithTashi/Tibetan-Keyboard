package com.kharagedition.tibetankeyboard.ui.keyboard

interface AIKeyboardInterface {
    fun getCurrentText(): String
    fun onGrammarReplace(originalText: String, correctedText: String)
    fun onTranslateReplace(originalText: String, translatedText: String)
    fun onRephraseReplace(originalText: String, rephrasedText: String)
    fun onAICancel()
    fun onSuggestionSelected(word: String)

    /** Open the AI chat screen (PRO users). */
    fun onOpenChat()

    /** Route a free user to the unlock flow (login if signed out, else the premium paywall). */
    fun onUnlockPro()

    /** Open the Tibetan Journey (streak & typing insights) screen. */
    fun onOpenJourney()
}
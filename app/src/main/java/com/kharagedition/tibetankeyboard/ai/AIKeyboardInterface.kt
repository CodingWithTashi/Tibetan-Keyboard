package com.kharagedition.tibetankeyboard.ai

interface AIKeyboardInterface {
    fun getCurrentText(): String
    fun onGrammarReplace(originalText: String, correctedText: String)
    fun onTranslateReplace(originalText: String, translatedText: String)
    fun onRephraseReplace(originalText: String, rephrasedText: String)
    fun onAICancel()
}
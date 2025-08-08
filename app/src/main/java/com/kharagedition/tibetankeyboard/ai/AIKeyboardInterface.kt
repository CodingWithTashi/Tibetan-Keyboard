package com.kharagedition.tibetankeyboard.ai

interface AIKeyboardInterface {
    fun onGrammarReplace(originalText: String, correctedText: String)
    fun onRephraseReplace(originalText: String, rephrasedText: String)
    fun onAICancel()
    fun getCurrentText(): String
}
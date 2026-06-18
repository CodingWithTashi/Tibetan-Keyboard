package com.kharagedition.tibetankeyboard.util

/**
 * Frontend mirror of the backend AI request limits.
 *
 * The single source of truth lives on the server in
 * `api-backend/src/config/constants.ts` (the `LIMITS` object). Keep these values
 * in sync: the backend rejects any request that exceeds them, so the app
 * validates locally first to give instant feedback and avoid burning a wasted
 * round-trip and paid AI tokens.
 */
object AiLimits {
    /**
     * Hard per-request input cap. The backend applies this GLOBALLY to every
     * text-bearing field (chat message / translate text) on every endpoint, for
     * BOTH free and pro users — it's the abuse guard against pasting a whole
     * document into a single call. Mirrors `LIMITS.MAX_INPUT_CHARS`.
     */
    const val MAX_INPUT_CHARS = 2000

    /** Minimum non-blank length. Mirrors `LIMITS.MIN_TEXT_LENGTH`. */
    const val MIN_TEXT_LENGTH = 1

    /**
     * Free-tier daily CHARACTER cap for translation (pro users unlimited).
     * Mirrors `LIMITS.DAILY_TRANSLATION_CHARS`. In-app these features are
     * pro-gated, so this is enforced server-side; kept here for parity.
     */
    const val DAILY_TRANSLATION_CHARS = 5000

    /**
     * Free-tier daily MESSAGE cap for chat (pro users unlimited).
     * Mirrors `LIMITS.FREE_DAILY_CHAT_MESSAGES`.
     */
    const val FREE_DAILY_CHAT_MESSAGES = 100

    /** True when [length] exceeds the hard per-request cap. */
    fun isOverInputLimit(length: Int): Boolean = length > MAX_INPUT_CHARS

    /** True when [text] is a valid, sendable input (non-blank and within the cap). */
    fun isValidInput(text: String): Boolean =
        text.trim().length in MIN_TEXT_LENGTH..MAX_INPUT_CHARS
}

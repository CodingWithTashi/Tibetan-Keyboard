package com.kharagedition.tibetankeyboard.data.local

import android.content.Context
import android.util.Base64
import com.kharagedition.tibetankeyboard.ui.journey.StreakLogic
import com.kharagedition.tibetankeyboard.ui.journey.StreakState
import java.security.MessageDigest
import java.util.TimeZone

/**
 * On-device typing statistics behind the "Tibetan Journey" streak feature.
 *
 * ### PRIVACY CONTRACT — do not weaken
 *  - Only aggregate **numbers** are stored: per-day character/word counts and the streak.
 *  - Typed words are never stored as text. Vocabulary size is tracked as a set of one-way
 *    SHA-256 hashes, so what the user typed cannot be read back — not even on this device.
 *  - Everything lives in this app-private SharedPreferences file and **never leaves the
 *    device** from here. (The optional community comparison on the Journey screen sends two
 *    aggregate numbers, and only after the user explicitly opts in.)
 *
 * The IME calls [recordTibetanChars]/[recordWordTyped] on the UI thread; both are cheap
 * (SharedPreferences writes are in-memory + async disk). Reads may come from the Journey
 * screen or the reminder worker on other threads — SharedPreferences is thread-safe.
 */
class TypingStatsStore private constructor(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Vocabulary hash set cached in memory so per-word updates don't re-read the store. */
    @Volatile
    private var vocabCache: MutableSet<String>? = null

    // ── time ─────────────────────────────────────────────────────────────────

    /** Local-timezone epoch day, so the streak rolls over at the user's midnight. */
    fun todayEpochDay(now: Long = System.currentTimeMillis()): Long =
        (now + TimeZone.getDefault().getOffset(now)) / MILLIS_PER_DAY

    // ── recording (called from the IME) ──────────────────────────────────────

    /**
     * Count [count] Tibetan code points typed. Also advances the streak on the first
     * activity of the day. Returns the streak milestone crossed today, if any, so the
     * caller can celebrate/log it — this store never logs analytics itself.
     */
    @Synchronized
    fun recordTibetanChars(count: Int): Int? {
        if (count <= 0) return null
        val today = todayEpochDay()
        prefs.edit().putInt(charsKey(today), charsOn(today) + count).apply()
        return touchStreak(today)
    }

    /**
     * Count one completed Tibetan word. [word] is hashed for the vocabulary size and the
     * text itself is discarded immediately — never persisted, never transmitted.
     */
    @Synchronized
    fun recordWordTyped(word: String): Int? {
        val trimmed = word.trim { it.isWhitespace() || it == TSHEK }
        if (trimmed.isEmpty() || !containsTibetan(trimmed)) return null
        val today = todayEpochDay()
        val edit = prefs.edit()
            .putInt(wordsKey(today), wordsOn(today) + 1)
            .putLong(KEY_TOTAL_WORDS, totalWords() + 1)

        val vocab = vocabSet()
        if (vocab.size < MAX_VOCAB_ENTRIES && vocab.add(hash(trimmed))) {
            edit.putStringSet(KEY_VOCAB_HASHES, HashSet(vocab))
        }
        edit.apply()
        return touchStreak(today)
    }

    /** Advance the streak for [today]; returns a crossed milestone (or null). */
    private fun touchStreak(today: Long): Int? {
        val before = streak()
        if (before.lastActiveEpochDay == today) return null // fast path: already counted
        val after = StreakLogic.recordActivity(before, today)
        prefs.edit()
            .putInt(KEY_STREAK_LEN, after.lengthDays)
            .putLong(KEY_STREAK_LAST_DAY, after.lastActiveEpochDay)
            .putInt(KEY_STREAK_BEST, after.bestDays)
            .apply()
        pruneOldDays(today)
        return StreakLogic.milestoneCrossed(before.lengthDays, after.lengthDays)
    }

    // ── reading (Journey screen / toolbar chip / reminder worker) ────────────

    fun streak(): StreakState = StreakState(
        lengthDays = prefs.getInt(KEY_STREAK_LEN, 0),
        lastActiveEpochDay = prefs.getLong(KEY_STREAK_LAST_DAY, -1L),
        bestDays = prefs.getInt(KEY_STREAK_BEST, 0),
    )

    /** The streak number to show right now (0 once a full day has been missed). */
    fun displayStreak(): Int = StreakLogic.displayLength(streak(), todayEpochDay())

    fun charsOn(epochDay: Long): Int = prefs.getInt(charsKey(epochDay), 0)

    fun wordsOn(epochDay: Long): Int = prefs.getInt(wordsKey(epochDay), 0)

    fun wordsToday(): Int = wordsOn(todayEpochDay())

    /** Words per day for the last 7 days, oldest first (today last). */
    fun weekWords(today: Long = todayEpochDay()): List<Int> =
        (6 downTo 0).map { wordsOn(today - it) }

    fun wordsThisWeek(today: Long = todayEpochDay()): Int = weekWords(today).sum()

    /** Tibetan characters typed in the last 7 days — feeds the PRO-autocomplete value teaser. */
    fun charsThisWeek(today: Long = todayEpochDay()): Int =
        (6 downTo 0).sumOf { charsOn(today - it) }

    fun totalWords(): Long = prefs.getLong(KEY_TOTAL_WORDS, 0L)

    /** Distinct Tibetan words ever typed (counted via one-way hashes — see privacy contract). */
    fun vocabularySize(): Int = vocabSet().size

    // ── community-comparison opt-in (numbers-only sync; default OFF) ─────────

    fun isSyncEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_ENABLED, false)

    fun setSyncEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()

    // ── weekly-notification guard (used by the reminder worker) ──────────────

    /** True once per ISO-ish week bucket; guards the weekly insight notification. */
    fun markWeeklyNotified(today: Long = todayEpochDay()): Boolean {
        val week = today / 7
        if (prefs.getLong(KEY_LAST_WEEKLY_NOTIFIED, -1L) == week) return false
        prefs.edit().putLong(KEY_LAST_WEEKLY_NOTIFIED, week).apply()
        return true
    }

    // ── internals ────────────────────────────────────────────────────────────

    private fun vocabSet(): MutableSet<String> {
        vocabCache?.let { return it }
        synchronized(this) {
            vocabCache?.let { return it }
            val loaded = HashSet(prefs.getStringSet(KEY_VOCAB_HASHES, emptySet()) ?: emptySet())
            vocabCache = loaded
            return loaded
        }
    }

    /** One-way, non-reversible fingerprint of a word (first 9 bytes of SHA-256). */
    private fun hash(word: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(word.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, 0, 9, Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun containsTibetan(s: String): Boolean = s.any { it.code in TIBETAN_BLOCK }

    /** Drop per-day counters older than [KEEP_DAYS]; runs at most once per streak advance. */
    private fun pruneOldDays(today: Long) {
        val cutoff = today - KEEP_DAYS
        val stale = prefs.all.keys.filter { key ->
            dayOfKey(key)?.let { it < cutoff } == true
        }
        if (stale.isEmpty()) return
        prefs.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    private fun charsKey(epochDay: Long) = "chars_d$epochDay"
    private fun wordsKey(epochDay: Long) = "words_d$epochDay"

    private fun dayOfKey(key: String): Long? {
        val prefix = when {
            key.startsWith("chars_d") -> "chars_d"
            key.startsWith("words_d") -> "words_d"
            else -> return null
        }
        return key.removePrefix(prefix).toLongOrNull()
    }

    companion object {
        private const val PREFS_NAME = "typing_stats"
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        private const val KEEP_DAYS = 60L
        private const val MAX_VOCAB_ENTRIES = 20_000
        private const val TSHEK = '་'
        private val TIBETAN_BLOCK = 0x0F00..0x0FFF

        private const val KEY_STREAK_LEN = "streak_len"
        private const val KEY_STREAK_LAST_DAY = "streak_last_day"
        private const val KEY_STREAK_BEST = "streak_best"
        private const val KEY_TOTAL_WORDS = "total_words"
        private const val KEY_VOCAB_HASHES = "vocab_hashes"
        private const val KEY_SYNC_ENABLED = "journey_sync_enabled"
        private const val KEY_LAST_WEEKLY_NOTIFIED = "last_weekly_notified"

        @Volatile
        private var instance: TypingStatsStore? = null

        fun getInstance(context: Context): TypingStatsStore =
            instance ?: synchronized(this) {
                instance ?: TypingStatsStore(context).also { instance = it }
            }

        /** True when [codePoint] belongs to the Tibetan Unicode block. */
        fun isTibetanCodePoint(codePoint: Int): Boolean = codePoint in TIBETAN_BLOCK
    }
}

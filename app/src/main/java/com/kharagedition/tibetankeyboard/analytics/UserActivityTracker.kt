package com.kharagedition.tibetankeyboard.analytics

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kharagedition.tibetankeyboard.data.local.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Maintains **queryable per-user activity aggregates** on `users/{uid}.activity` so the most active
 * users can be pulled with a single Firestore query — something neither Firebase Analytics (GA4, which
 * only feeds aggregate dashboards, see [AppAnalytics]) nor the raw per-event `user_analytics` log
 * (which would have to be aggregated client-side) can answer cheaply.
 *
 * ### What lands on `users/{uid}.activity`
 * - `lastActiveAt`     — epoch millis of the user's most recent active day (recency filter/sort).
 * - `lastActiveDay`    — `yyyy-MM-dd` (UTC) of that day, human-readable.
 * - `activeDaysCount`  — distinct days the user was ever active (monotonic). The canonical
 *                        "most active / most engaged" metric.
 * - `lastActiveSource` — `app` or `keyboard`, the surface that recorded the latest day.
 *
 * ### Cost
 * At most **one Firestore write per user per UTC day**. An in-memory fast-path skips work entirely
 * once the day's ping is done, and a durable [UserPreferences]-style guard (its own prefs file)
 * survives process restarts so a cold start mid-day doesn't double-write.
 *
 * ### How to read it back (admin/console/BigQuery — not wired in-app)
 * ```
 * // Most engaged users (power users):
 * db.collection("users")
 *   .orderBy("activity.activeDaysCount", Query.Direction.DESCENDING)
 *   .limit(50)
 *
 * // Recently active users (last 7 days):
 * val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
 * db.collection("users")
 *   .whereGreaterThan("activity.lastActiveAt", cutoff)
 *   .orderBy("activity.lastActiveAt", Query.Direction.DESCENDING)
 * ```
 *
 * The `activity` map is intentionally a **separate top-level field** from the `User.analytics`
 * object: `UserRepository.createOrUpdateUser` rewrites `analytics` wholesale on every login, which
 * would clobber an incrementing counter living inside it. We only ever touch `activity` via merged
 * dotted-path writes here.
 */
object UserActivityTracker {

    /** Where the activity ping originated, persisted as `activity.lastActiveSource`. */
    enum class Source(val tag: String) {
        APP("app"),
        KEYBOARD("keyboard"),
    }

    private const val TAG = "UserActivityTracker"
    private const val PREFS = "user_activity"
    private const val KEY_LAST_PING_EPOCH_DAY = "last_ping_epoch_day"
    private const val KEY_LAST_PING_UID = "last_ping_uid"

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    private val db get() = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** In-memory fast-path: once today's ping is written we skip even launching a coroutine. */
    @Volatile private var memoEpochDay: Long = -1
    @Volatile private var memoUid: String? = null

    /**
     * Record that the signed-in user is active right now. Fire-and-forget and idempotent within a
     * UTC day — safe to call on every Activity resume / keyboard show. No-op when no user is signed
     * in (anonymous activity can't be attributed to a user).
     */
    fun recordActive(context: Context, source: Source) {
        val uid = resolveUid(context)
        if (uid.isNullOrBlank()) return

        val epochDay = System.currentTimeMillis() / MILLIS_PER_DAY
        // Already pinged for this user today — nothing to do (no coroutine, no disk, no network).
        if (epochDay == memoEpochDay && uid == memoUid) return

        val appContext = context.applicationContext
        scope.launch { maybeWrite(appContext, uid, epochDay, source) }
    }

    /** Durable throttle + the actual Firestore write. Runs off the main thread. */
    private fun maybeWrite(context: Context, uid: String, epochDay: Long, source: Source) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastDay = prefs.getLong(KEY_LAST_PING_EPOCH_DAY, -1)
        val lastUid = prefs.getString(KEY_LAST_PING_UID, null)

        // Already recorded for this user on this UTC day (survives process restarts).
        if (epochDay == lastDay && uid == lastUid) {
            memoEpochDay = epochDay
            memoUid = uid
            return
        }

        val now = System.currentTimeMillis()
        val activity = mapOf(
            "lastActiveAt" to now,
            "lastActiveDay" to utcDay(now),
            "lastActiveSource" to source.tag,
            // Count a distinct active day. Increment (not a plain write) is atomic and never races.
            "activeDaysCount" to FieldValue.increment(1),
        )

        // merge() so this both creates the field if absent and never disturbs other user fields.
        db.collection("users").document(uid)
            .set(mapOf("activity" to activity), SetOptions.merge())
            .addOnSuccessListener {
                memoEpochDay = epochDay
                memoUid = uid
                prefs.edit()
                    .putLong(KEY_LAST_PING_EPOCH_DAY, epochDay)
                    .putString(KEY_LAST_PING_UID, uid)
                    .apply()
            }
            .addOnFailureListener { e ->
                // Best-effort: a failed ping just means we retry on the next activity. Don't persist
                // the throttle so the next attempt is allowed to write.
                Log.w(TAG, "activity ping failed for uid=$uid (will retry next activity)", e)
            }
    }

    /** Prefer the live Firebase session; fall back to the persisted uid (e.g. inside the IME). */
    private fun resolveUid(context: Context): String? {
        FirebaseAuth.getInstance().currentUser?.uid?.let { if (it.isNotBlank()) return it }
        return UserPreferences(context).getUserId().takeIf { it.isNotBlank() }
    }

    private fun utcDay(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))
}

package com.kharagedition.tibetankeyboard.data.repository

import android.util.Log
import com.kharagedition.tibetankeyboard.data.model.JourneyWeeklyData
import com.kharagedition.tibetankeyboard.data.model.JourneyWeeklyRequest
import com.kharagedition.tibetankeyboard.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backend calls for the Journey feature's opt-in community comparison.
 * Sends two aggregate numbers (weekly word count + streak days) — never typed content.
 */
class JourneyRepository {

    /** Submit this week's numbers; returns the community percentile, or null on any failure. */
    suspend fun submitWeekly(userId: String, wordsThisWeek: Int, streakDays: Int): JourneyWeeklyData? =
        withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.journeyAPI.submitWeekly(
                    JourneyWeeklyRequest(wordsThisWeek = wordsThisWeek, streakDays = streakDays),
                    userId,
                )
                if (response.success) response.data else null
            } catch (e: Exception) {
                Log.w(TAG, "submitWeekly failed: ${e.message}")
                null
            }
        }

    companion object {
        private const val TAG = "JourneyRepository"
    }
}

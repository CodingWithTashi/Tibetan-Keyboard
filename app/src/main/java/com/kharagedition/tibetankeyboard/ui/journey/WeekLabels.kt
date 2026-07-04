package com.kharagedition.tibetankeyboard.ui.journey

import java.util.Calendar

/** Single-letter weekday labels under the Journey week chart — matches [TypingStatsStore.weekWords]'s local-day window. */
object WeekLabels {

    private val LETTERS = mapOf(
        Calendar.SUNDAY to "S",
        Calendar.MONDAY to "M",
        Calendar.TUESDAY to "T",
        Calendar.WEDNESDAY to "W",
        Calendar.THURSDAY to "T",
        Calendar.FRIDAY to "F",
        Calendar.SATURDAY to "S",
    )

    /** Labels for the last 7 local days, oldest first, today last — same order as [TypingStatsStore.weekWords]. */
    fun lastSevenDays(now: Long = System.currentTimeMillis()): List<String> {
        val today = Calendar.getInstance().apply { timeInMillis = now }
        return (6 downTo 0).map { daysAgo ->
            val day = today.clone() as Calendar
            day.add(Calendar.DAY_OF_YEAR, -daysAgo)
            LETTERS.getValue(day.get(Calendar.DAY_OF_WEEK))
        }
    }
}

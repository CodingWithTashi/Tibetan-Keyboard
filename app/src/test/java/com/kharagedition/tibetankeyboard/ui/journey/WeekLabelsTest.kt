package com.kharagedition.tibetankeyboard.ui.journey

import org.junit.Assert.assertEquals
import java.util.Calendar
import org.junit.Test

class WeekLabelsTest {

    @Test
    fun `seven labels ending on today, oldest first`() {
        // 2024-01-13 was a Saturday.
        val cal = Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 13, 12, 0, 0) }
        assertEquals(
            listOf("S", "M", "T", "W", "T", "F", "S"), // Sun 7th .. Sat 13th
            WeekLabels.lastSevenDays(cal.timeInMillis),
        )
    }

    @Test
    fun `today is always the last label`() {
        // 2024-03-04 was a Monday.
        val cal = Calendar.getInstance().apply { set(2024, Calendar.MARCH, 4, 9, 30, 0) }
        assertEquals("M", WeekLabels.lastSevenDays(cal.timeInMillis).last())
    }
}

package com.kharagedition.tibetankeyboard.util

import java.util.TimeZone

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * Days since the epoch in the device's local time zone, so "today" rolls over at local midnight.
 * Shared by the typing streak and the daily free-suggestion quota.
 */
fun localEpochDay(now: Long = System.currentTimeMillis()): Long =
    (now + TimeZone.getDefault().getOffset(now)) / MILLIS_PER_DAY

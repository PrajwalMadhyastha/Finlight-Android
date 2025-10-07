// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/utils/DateUtils.kt
// REASON: NEW FILE - Centralizes all date-related calculations. This ensures
// that different parts of the app (like the dashboard card and the reports screen)
// use the exact same logic for determining date ranges, guaranteeing data consistency.
// =================================================================================
package io.pm.finlight.utils

import java.util.Calendar

object DateUtils {

    /**
     * Calculates the start and end timestamps for the previous full month.
     * @param currentTimeMillis The timestamp to calculate the previous month relative to. Defaults to the current system time.
     * @return A Pair containing the start (inclusive) and end (inclusive) milliseconds.
     */
    fun getPreviousMonthDateRange(currentTimeMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
        }
        // Move calendar to the previous month
        calendar.add(Calendar.MONTH, -1)

        // Calculate the start of that month
        val start = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Calculate the end of that month
        val end = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        return Pair(start, end)
    }
}
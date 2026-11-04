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
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = currentTimeMillis
            }
        // Move calendar to the previous month
        calendar.add(Calendar.MONTH, -1)

        // Calculate the start of that month
        val start =
            (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        // Calculate the end of that month
        val end =
            (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

        return Pair(start, end)
    }

    /**
     * Calculates the start and end timestamps for the current month.
     * @param calendar The calendar instance to use for calculation.
     * @return A Pair containing the start (inclusive) and end (inclusive) milliseconds.
     */
    fun getCurrentMonthDateRange(calendar: Calendar = Calendar.getInstance()): Pair<Long, Long> {
        val start =
            (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        val end =
            (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

        return Pair(start, end)
    }

    /**
     * Calculates the next due date for a recurring transaction.
     */
    fun calculateNextDueDate(
        lastRunOrStartDate: Long,
        interval: String
    ): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = lastRunOrStartDate }
        when (interval.lowercase()) {
            "daily" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            "weekly" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            "monthly" -> calendar.add(Calendar.MONTH, 1)
            "yearly" -> calendar.add(Calendar.YEAR, 1)
        }
        return calendar.timeInMillis
    }

    /**
     * Calculates the start (inclusive) and end (inclusive) timestamps for a credit card billing cycle.
     *
     * @param billingCycleDay The nominal statement date (1..31).
     * @param currentTimeMillis The timestamp relative to which the current cycle is calculated.
     * @return Pair containing start timestamp (00:00:00.000) and end timestamp (23:59:59.999).
     */
    fun getCreditCardBillingCycleRange(
        billingCycleDay: Int,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): Pair<Long, Long> {
        val clampedDay = billingCycleDay.coerceIn(1, 31)
        val now = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val currentDay = now.get(Calendar.DAY_OF_MONTH)

        val startCal = now.clone() as Calendar
        val endCal = now.clone() as Calendar

        if (currentDay <= clampedDay) {
            // Today is on or before the statement date -> cycle ends this month on the billing day.
            val maxDaysInCurrentMonth = endCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val endDay = clampedDay.coerceAtMost(maxDaysInCurrentMonth)
            endCal.apply {
                set(Calendar.DAY_OF_MONTH, endDay)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            // Cycle started the day after the previous month's statement date.
            startCal.apply {
                add(Calendar.MONTH, -1)
                val maxDaysInPrevMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
                val prevDay = clampedDay.coerceAtMost(maxDaysInPrevMonth)
                set(Calendar.DAY_OF_MONTH, prevDay)
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } else {
            // Today is after the statement date -> cycle started the day after this month's billing day.
            startCal.apply {
                val maxDaysInCurrentMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
                val thisDay = clampedDay.coerceAtMost(maxDaysInCurrentMonth)
                set(Calendar.DAY_OF_MONTH, thisDay)
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Cycle ends next month on the billing day.
            endCal.apply {
                add(Calendar.MONTH, 1)
                val maxDaysInNextMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
                val nextDay = clampedDay.coerceAtMost(maxDaysInNextMonth)
                set(Calendar.DAY_OF_MONTH, nextDay)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
        }

        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }
}

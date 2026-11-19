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
     * The cycle starts on the configured [billingCycleDay] (00:00:00.000) and ends at 23:59:59.999
     * on the day immediately preceding the start of the next cycle.
     *
     * For example:
     * - Day 1: Runs from 1st 00:00:00.000 to the last day of the current month 23:59:59.999.
     * - Day 15: Runs from 15th 00:00:00.000 to 14th 23:59:59.999 of the next month.
     *
     * @param billingCycleDay The nominal cycle start date (1..31).
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

        val maxDaysCurrentMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cycleStartDayCurrentMonth = clampedDay.coerceAtMost(maxDaysCurrentMonth)

        val startCal =
            (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        val nextCycleStartCal =
            (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        if (currentDay >= cycleStartDayCurrentMonth) {
            // Active cycle started in the current calendar month
            startCal.set(Calendar.DAY_OF_MONTH, cycleStartDayCurrentMonth)

            // Next cycle begins next month on clampedDay (clamped to next month's length)
            nextCycleStartCal.apply {
                add(Calendar.MONTH, 1)
                val maxDaysNextMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, clampedDay.coerceAtMost(maxDaysNextMonth))
            }
        } else {
            // Active cycle started in the previous calendar month
            startCal.apply {
                add(Calendar.MONTH, -1)
                val maxDaysPrevMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, clampedDay.coerceAtMost(maxDaysPrevMonth))
            }

            // Next cycle begins in the current calendar month on cycleStartDayCurrentMonth
            nextCycleStartCal.set(Calendar.DAY_OF_MONTH, cycleStartDayCurrentMonth)
        }

        // Active cycle ends 1 millisecond before next cycle begins (i.e. 23:59:59.999 of preceding day)
        val endMillis = nextCycleStartCal.timeInMillis - 1L

        return Pair(startCal.timeInMillis, endMillis)
    }
}

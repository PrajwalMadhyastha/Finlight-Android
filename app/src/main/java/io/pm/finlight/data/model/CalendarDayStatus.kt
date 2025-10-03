package io.pm.finlight

import java.util.Date

/**
 * Holds the processed status and data for a single day in the consistency calendar.
 *
 * @param date The specific date this status applies to.
 * @param status The calculated spending status for this day.
 * @param amountSpent The total amount spent on this day.
 * @param safeToSpend The calculated "safe to spend" amount for this day, derived from the monthly budget.
 */
data class CalendarDayStatus(
    val date: Date,
    val status: SpendingStatus,
    val amountSpent: Long,
    val safeToSpend: Long
)
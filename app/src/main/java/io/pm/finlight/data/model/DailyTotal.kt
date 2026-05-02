package io.pm.finlight

/**
 * A simple data class to hold the results of a GROUP BY query,
 * containing a date string and the total amount spent on that day.
 */
data class DailyTotal(
    val date: String, // Format: "YYYY-MM-DD"
    val totalAmount: Double,
)

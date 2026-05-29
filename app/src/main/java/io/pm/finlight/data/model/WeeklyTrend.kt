package io.pm.finlight

/**
 * Data class to hold weekly trend data with both income and expenses.
 */
data class WeeklyTrend(
    // Format: "YYYY-WW" (year-week)
    val period: String,
    val totalIncome: Double,
    val totalExpenses: Double,
)

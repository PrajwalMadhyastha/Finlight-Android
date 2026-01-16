package io.pm.finlight

/**
 * Data class to hold weekly trend data with both income and expenses.
 */
data class WeeklyTrend(
    val period: String, // Format: "YYYY-WW" (year-week)
    val totalIncome: Double,
    val totalExpenses: Double
)

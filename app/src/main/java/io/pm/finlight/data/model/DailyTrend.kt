package io.pm.finlight

/**
 * Data class to hold daily trend data with both income and expenses.
 */
data class DailyTrend(
    val date: String, // Format: "YYYY-MM-DD"
    val totalIncome: Double,
    val totalExpenses: Double
)

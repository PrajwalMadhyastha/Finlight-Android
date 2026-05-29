package io.pm.finlight

/**
 * Data class to hold daily trend data with both income and expenses.
 */
data class DailyTrend(
    // Format: "YYYY-MM-DD"
    val date: String,
    val totalIncome: Double,
    val totalExpenses: Double,
)

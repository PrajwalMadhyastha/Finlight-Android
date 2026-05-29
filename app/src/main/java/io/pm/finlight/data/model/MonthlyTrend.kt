package io.pm.finlight

data class MonthlyTrend(
    // Format: "YYYY-MM"
    val monthYear: String,
    val totalIncome: Double,
    val totalExpenses: Double,
)

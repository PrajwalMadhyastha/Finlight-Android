package io.pm.finlight

/**
 * A data class to hold a financial summary, typically for a specific date range.
 * This is used for fetching aggregated income and expense data from the database.
 *
 * @param totalIncome The sum of all income transactions in the period.
 * @param totalExpenses The sum of all expense transactions in the period.
 */
data class FinancialSummary(
    val totalIncome: Double,
    val totalExpenses: Double,
)

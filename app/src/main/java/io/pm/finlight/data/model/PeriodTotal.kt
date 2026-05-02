// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/PeriodTotal.kt
// REASON: NEW FILE - This data class is required by the new DAO queries to hold
// the aggregated results for weekly and monthly spending totals.
// =================================================================================
package io.pm.finlight

/**
 * A simple data class to hold the results of a GROUP BY query for weekly or monthly totals.
 *
 * @param period The period identifier (e.g., "2025-27" for week 27, or "2025-07" for July).
 * @param totalAmount The sum of all expenses for this period.
 */
data class PeriodTotal(
    val period: String,
    val totalAmount: Double,
)

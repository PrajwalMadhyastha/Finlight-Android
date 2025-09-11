// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/model/SpendingAnalysisItem.kt
// REASON: NEW FILE - This data class holds the aggregated results for the new
// Spending Analysis screen. It's a generic container for grouped data by
// category, tag, or merchant.
// =================================================================================
package io.pm.finlight.data.model

/**
 * A data class to hold aggregated spending data for a specific dimension.
 *
 * @param dimensionId The unique identifier for the dimension (e.g., category ID, tag ID, or merchant name).
 * @param dimensionName The display name of the dimension (e.g., "Food", "Travel", "Amazon").
 * @param totalAmount The sum of all expenses for this dimension in a given period.
 * @param transactionCount The number of transactions for this dimension.
 */
data class SpendingAnalysisItem(
    val dimensionId: String,
    val dimensionName: String,
    val totalAmount: Double,
    val transactionCount: Int
)
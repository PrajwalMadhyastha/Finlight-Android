package io.pm.finlight

/**
 * A data class to hold aggregated spending data for a specific merchant.
 *
 * @param merchantName The name of the merchant (from the transaction description).
 * @param totalAmount The sum of all expenses for this merchant in a given period.
 * @param transactionCount The number of transactions (visits) for this merchant.
 */
data class MerchantSpendingSummary(
    val merchantName: String,
    val totalAmount: Double,
    val transactionCount: Int,
)

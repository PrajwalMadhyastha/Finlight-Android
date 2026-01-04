// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/model/MerchantPrediction.kt
// REASON: FEATURE (Quick Fill) - Added `accountName` to the prediction model.
// This allows the UI to display which account was previously used for a merchant,
// providing better context to the user before they select a suggestion.
// =================================================================================
package io.pm.finlight.data.model

/**
 * A data class to hold a predicted merchant and its associated category.
 * This is the result of a query that finds unique merchant/category pairs
 * from the user's transaction history.
 *
 * @param description The merchant name.
 * @param categoryId The ID of the associated category.
 * @param categoryName The name of the associated category.
 * @param categoryIconKey The icon key for the category's UI representation.
 * @param categoryColorKey The color key for the category's UI representation.
 * @param accountId The ID of the account used for this transaction.
 * @param accountName The name of the account used for this transaction.
 */
data class MerchantPrediction(
    val description: String,
    val categoryId: Int?,
    val categoryName: String?,
    val categoryIconKey: String?,
    val categoryColorKey: String?,
    val accountId: Int?,
    val accountName: String?
)

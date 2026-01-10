// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/model/MerchantPrediction.kt
// REASON: FIX (Crash Prevention) - Added `uniqueKey` property. This centralizes
// the key generation logic used in LazyColumns to ensure it's consistent across
// the app (Carousel vs Sheet) and unique enough to prevent "Key already used"
// crashes when the same merchant/category is used with different accounts.
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
) {
    /**
     * Generates a unique key for this prediction, suitable for use in
     * LazyColumn or LazyRow items.
     */
    val uniqueKey: String
        get() = "${description}_${categoryId ?: 0}_${accountId ?: 0}"
}

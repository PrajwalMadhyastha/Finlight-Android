// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/model/MerchantPrediction.kt
// REASON: NEW FILE - This Data Transfer Object (DTO) is created to hold the
// results of the new merchant search query. It combines the merchant name with
// its most frequently associated category details, providing all necessary data
// for the smart prediction UI in a single, clean object.
// =================================================================================
package io.pm.finlight

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
 */
data class MerchantPrediction(
    val description: String,
    val categoryId: Int?,
    val categoryName: String?,
    val categoryIconKey: String?,
    val categoryColorKey: String?
)

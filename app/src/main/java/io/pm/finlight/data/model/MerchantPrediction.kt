// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/model/MerchantPrediction.kt
// REASON: FIX - The package name has been corrected to match its file path.
// This resolves the "Unresolved reference" build error in downstream files like
// the DAO and Repository by making the DTO properly accessible.
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
 */
data class MerchantPrediction(
    val description: String,
    val categoryId: Int?,
    val categoryName: String?,
    val categoryIconKey: String?,
    val categoryColorKey: String?
)

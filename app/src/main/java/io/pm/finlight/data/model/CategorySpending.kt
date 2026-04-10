package io.pm.finlight

/**
 * A simple data class to hold the results of a GROUP BY query,
 * containing the name of a category, its visual identifiers, and the total amount spent in it.
 */
data class CategorySpending(
    val categoryName: String,
    val totalAmount: Double,
    val colorKey: String?,
    val iconKey: String?,
)

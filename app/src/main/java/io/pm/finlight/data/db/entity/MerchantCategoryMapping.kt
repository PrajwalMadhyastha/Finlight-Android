package io.pm.finlight

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Stores a user-defined mapping between a parsed merchant name and a specific category.
 * This allows the app to "learn" user preferences and auto-categorize future transactions.
 *
 * @param parsedName The merchant name as it was originally parsed from an SMS or other source. This is the key.
 * @param categoryId The ID of the Category the user has associated with this merchant.
 */
@Serializable
@Entity(tableName = "merchant_category_mapping")
data class MerchantCategoryMapping(
    @PrimaryKey
    val parsedName: String,
    val categoryId: Int
)

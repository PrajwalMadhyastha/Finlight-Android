// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/entity/MerchantCategoryMapping.kt
// REASON: FIX (Audit #228 / F-11) - Added @ForeignKey constraint on categoryId.
// Previously, if a category was deleted, orphaned MerchantCategoryMapping rows would
// remain, causing the auto-categorisation engine to reference a non-existent category ID.
// ON DELETE CASCADE removes the mapping when the referenced category is deleted, keeping
// the table consistent. A matching @Index is required by Room for FK columns.
// =================================================================================
package io.pm.finlight

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Stores a user-defined mapping between a parsed merchant name and a specific category.
 * This allows the app to "learn" user preferences and auto-categorize future transactions.
 *
 * @param parsedName The merchant name as it was originally parsed from an SMS or other source. This is the key.
 * @param categoryId The ID of the Category the user has associated with this merchant.
 *                   When the referenced category is deleted, this mapping row is also removed (ON DELETE CASCADE).
 */
@Serializable
@Entity(
    tableName = "merchant_category_mapping",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["categoryId"])],
)
data class MerchantCategoryMapping(
    @PrimaryKey
    val parsedName: String,
    val categoryId: Int,
)

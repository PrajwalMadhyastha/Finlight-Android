// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/SplitTransactionDetails.kt
// REASON: NEW FILE - This is a Data Transfer Object (DTO) that combines a
// SplitTransaction with its corresponding Category details (name, icon, color).
// It's used by Room to return the complete data needed for the UI in a single query.
// =================================================================================
package io.pm.finlight

import androidx.room.Embedded

data class SplitTransactionDetails(
    @Embedded
    val splitTransaction: SplitTransaction,
    val categoryName: String?,
    val categoryIconKey: String?,
    val categoryColorKey: String?,
)

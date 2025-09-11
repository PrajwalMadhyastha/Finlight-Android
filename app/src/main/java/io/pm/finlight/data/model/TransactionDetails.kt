// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/model/TransactionDetails.kt
// REASON: FEATURE - Added a new nullable `tagNames` string property. This allows
// the DAO to aggregate tag names directly into the DTO using a GROUP_CONCAT
// query, which is highly efficient for display in the transaction list.
// =================================================================================
package io.pm.finlight

import androidx.room.Embedded
import androidx.room.Relation

data class TransactionDetails(
    @Embedded
    val transaction: Transaction,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val images: List<TransactionImage>,
    val accountName: String?,
    val categoryName: String?,
    val categoryIconKey: String?,
    val categoryColorKey: String?,
    val tagNames: String? // --- NEW: To hold comma-separated tag names
)
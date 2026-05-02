// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/TransactionWithSplits.kt
// REASON: NEW FILE - This is a Data Transfer Object (DTO) used by Room to return
// a parent transaction along with its list of child split items. The @Relation
// annotation tells Room how to join the two entities.
// =================================================================================
package io.pm.finlight

import androidx.room.Embedded
import androidx.room.Relation

data class TransactionWithSplits(
    @Embedded
    val transaction: Transaction,
    @Relation(
        parentColumn = "id",
        entityColumn = "parentTransactionId",
    )
    val splits: List<SplitTransaction>,
)

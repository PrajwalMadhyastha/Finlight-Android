// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/entity/GoalTransactionLink.kt
// REASON: NEW FILE (Issue #104) - Junction table linking transactions to savings
// goals. This enables dynamic progress tracking by computing a goal's saved amount
// as the SUM of linked transaction amounts, replacing the manual savedAmount field.
// =================================================================================
package io.pm.finlight.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.pm.finlight.Goal
import io.pm.finlight.Transaction
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "goal_transaction_links",
    primaryKeys = ["goalId", "transactionId"],
    foreignKeys = [
        ForeignKey(
            entity = Goal::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goalId"), Index("transactionId")],
)
data class GoalTransactionLink(
    val goalId: Int,
    val transactionId: Int,
    val linkedAt: Long = System.currentTimeMillis(),
)

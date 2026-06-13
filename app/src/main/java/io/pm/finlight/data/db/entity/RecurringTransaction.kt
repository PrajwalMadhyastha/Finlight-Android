// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/RecurringTransaction.kt
// REASON: FEATURE - Added a new nullable `lastRunDate` field. This will store
// the timestamp of the last time a transaction was created from this rule,
// which is essential for the RecurringTransactionWorker to determine the next
// due date.
// =================================================================================
package io.pm.finlight

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "recurring_transactions",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val description: String,
    val amount: Double,
    // "income" or "expense"
    val transactionType: String,
    // e.g., "Daily", "Weekly", "Monthly", "Yearly"
    val recurrenceInterval: String,
    // Timestamp for the first occurrence
    val startDate: Long,
    val accountId: Int,
    val categoryId: Int?,
    // --- Track the last execution date of this rule ---
    val lastRunDate: Long? = null,
    // --- NEW: The SMS sender ID for variable bills (e.g. "AM-BESCOM") ---
    val smsSenderId: String? = null,
    // --- NEW: If true, amount is variable and determined from SMS; Amount field is baseline ---
    val isVariableBill: Boolean = false,
    // --- NEW: If true, skip user confirmation when SMS perfectly matches ---
    val autoApprove: Boolean = false,
    // --- NEW: Optional end date for free-trial or limited-period rules ---
    val endDate: Long? = null,
    // --- NEW: Tracks consecutive cycles missed/skipped (groundwork for cancellation detection) ---
    val skipCount: Int = 0,
)

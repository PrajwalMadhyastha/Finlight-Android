package io.pm.finlight.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.pm.finlight.Transaction

/**
 * Snapshots the pre-merge state of the parent transaction and the full child
 * transaction data at the moment [mergeTransactions] is called.
 *
 * Purpose: Allow the user to fully reverse an accidental merge at any time by
 * restoring the parent to its original state and re-inserting the child.
 *
 * The CASCADE delete on [parentTxnId] ensures that if the parent transaction is
 * ever deleted, this record is automatically cleaned up — no orphan rows possible.
 */
@Entity(
    tableName = "merge_records",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["parentTxnId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentTxnId")],
)
data class MergeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** The ID of the surviving (parent) transaction. */
    val parentTxnId: Int,
    /** Timestamp of when the merge was performed. */
    val mergedAt: Long = System.currentTimeMillis(),
    // ─── Parent snapshot ─────────────────────────────────────────────────────
    // The values the PARENT had immediately BEFORE the merge occurred.
    // Needed to restore the parent if the user requests an unmerge.
    val originalParentAmount: Double,
    val originalParentDate: Long,
    val originalParentNotes: String?,
    // ─── Child snapshot ───────────────────────────────────────────────────────
    // All fields required to reconstruct the deleted child transaction as a new row.
    val childDescription: String,
    val childAmount: Double,
    val childDate: Long,
    val childAccountId: Int,
    val childCategoryId: Int?,
    val childTransactionType: String,
    val childSource: String,
    val childNotes: String?,
    val childSourceSmsId: Long?,
    val childSourceSmsHash: String?,
    val childSmsSignature: String?,
    val childOriginalDescription: String?,
    val childOriginalAmount: Double?,
    val childCurrencyCode: String?,
    val childConversionRate: Double?,
)

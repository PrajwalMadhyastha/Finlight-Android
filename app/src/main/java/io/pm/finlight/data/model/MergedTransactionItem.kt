package io.pm.finlight.data.model

import io.pm.finlight.TransactionType

/**
 * Represents a single transaction's contribution to a manually or automatically
 * merged transaction. Used to power the [MergedTransactionsCard] on the
 * Transaction Detail screen, which displays a breakdown of all transactions
 * that were involved in a merge.
 *
 * The data is reconstructed at display-time from the [MergeRecord] table
 * without any additional schema changes.
 *
 * @param accountId The Room ID of the contributing account.
 * @param accountName The display name of the account (e.g. "HDFC Savings").
 * @param amount The original amount this transaction contributed before the merge.
 * @param transactionType [TransactionType] — controls the sign (+ / −) displayed.
 * @param isAnchor True for the surviving (anchor) transaction; false for merged children.
 * @param description The original description of the transaction.
 * @param date The original date of the transaction.
 */
data class MergedTransactionItem(
    val accountId: Int,
    val accountName: String,
    val amount: Double,
    val transactionType: TransactionType,
    val isAnchor: Boolean,
    val description: String,
    val date: Long,
)

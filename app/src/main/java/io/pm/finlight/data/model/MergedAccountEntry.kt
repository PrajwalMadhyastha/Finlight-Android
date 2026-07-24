package io.pm.finlight.data.model

/**
 * Represents a single account's contribution to a manually or automatically
 * merged transaction. Used to power the [MergedAccountsCard] on the
 * Transaction Detail screen, which displays a breakdown of all accounts
 * that were involved in a cross-account merge.
 *
 * The data is reconstructed at display-time from the [MergeRecord] table
 * without any additional schema changes.
 *
 * @param accountId The Room ID of the contributing account.
 * @param accountName The display name of the account (e.g. "HDFC Savings").
 * @param amount The original amount this account contributed before the merge.
 * @param transactionType "income" or "expense" — controls the sign (+ / −) displayed.
 * @param isAnchor True for the surviving (anchor) transaction's account; false for merged children.
 */
data class MergedAccountEntry(
    val accountId: Int,
    val accountName: String,
    val amount: Double,
    val transactionType: String,
    val isAnchor: Boolean,
)

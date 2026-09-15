// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/TransactionReimbursementDao.kt
// REASON: REFACTOR (Domain DAO Decomposition - Issue #237) - Dedicated DAO for all
// reimbursement queries, parent/child linking, and candidate lookups.
// =================================================================================
package io.pm.finlight.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction as RoomTransaction
import io.pm.finlight.TransactionDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionReimbursementDao {
    /**
     * Returns all income transactions linked to a given expense as reimbursements,
     * with full account/category join for display in the detail screen.
     */
    @RoomTransaction
    @Query(
        """
        SELECT T.*, A.name as accountName, C.name as categoryName, C.iconKey as categoryIconKey, C.colorKey as categoryColorKey,
        (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM transactions AS T
        LEFT JOIN accounts AS A ON T.accountId = A.id
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE T.parentReimbursementId = :expenseId
        ORDER BY T.date DESC
    """,
    )
    fun getReimbursementsForExpense(expenseId: Int): Flow<List<TransactionDetails>>

    @Query("SELECT COUNT(*) FROM transactions WHERE parentReimbursementId = :expenseId")
    fun getReimbursementsCountSync(expenseId: Int): Int

    /**
     * Returns the expense transaction that a given income is linked to as a reimbursement.
     * Used to show a badge on the income transaction's detail screen.
     */
    @RoomTransaction
    @Query(
        """
        SELECT T.*, A.name as accountName, C.name as categoryName, C.iconKey as categoryIconKey, C.colorKey as categoryColorKey,
        (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM transactions AS T
        LEFT JOIN accounts AS A ON T.accountId = A.id
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE T.id = (SELECT parentReimbursementId FROM transactions WHERE id = :incomeId)
    """,
    )
    fun getLinkedExpenseForReimbursement(incomeId: Int): Flow<TransactionDetails?>

    /**
     * Links an income transaction to an expense as a reimbursement.
     */
    @Query("UPDATE transactions SET parentReimbursementId = :expenseId, isExcluded = 1, linkedSurplusTxnId = :surplusTxnId WHERE id = :incomeId")
    suspend fun linkReimbursement(
        incomeId: Int,
        expenseId: Int,
        surplusTxnId: Int? = null,
    )

    /**
     * Removes the link from an income transaction, restoring it as a standalone credit.
     */
    @Query("UPDATE transactions SET parentReimbursementId = NULL, isExcluded = 0, linkedSurplusTxnId = NULL WHERE id = :incomeId")
    suspend fun unlinkReimbursement(incomeId: Int)

    /**
     * Returns recent income transactions that are NOT yet linked as reimbursements,
     * to populate the picker sheet when the user wants to link a repayment.
     */
    @RoomTransaction
    @Query(
        """
        SELECT T.*, A.name as accountName, C.name as categoryName, C.iconKey as categoryIconKey, C.colorKey as categoryColorKey,
        (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM transactions AS T
        LEFT JOIN accounts AS A ON T.accountId = A.id
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE T.transactionType = $SQL_INCOME
          AND T.parentReimbursementId IS NULL
          AND T.id != :excludeExpenseId
        ORDER BY T.date DESC
        LIMIT 50
    """,
    )
    fun getCandidateReimbursements(excludeExpenseId: Int): Flow<List<TransactionDetails>>
}

// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/TransactionWriteDao.kt
// REASON: REFACTOR (Domain DAO Decomposition - Issue #237) - Dedicated DAO for all
// INSERT, UPDATE, DELETE, and batch mutation operations on transactions.
// =================================================================================
package io.pm.finlight.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionImage
import io.pm.finlight.TransactionTagCrossRef
import io.pm.finlight.TransactionType

@Dao
interface TransactionWriteDao {
    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id IN (:transactionIds)")
    suspend fun deleteByIds(transactionIds: List<Int>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("UPDATE transactions SET description = :description WHERE id = :id")
    suspend fun updateDescription(
        id: Int,
        description: String,
    )

    @Query("UPDATE transactions SET amount = :amount WHERE id = :id")
    suspend fun updateAmount(
        id: Int,
        amount: Double,
    )

    @Query("UPDATE transactions SET amount = :amount, originalAmount = :amount WHERE id = :id")
    suspend fun updateManualAmountEdit(
        id: Int,
        amount: Double,
    )

    @Query("UPDATE transactions SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(
        id: Int,
        notes: String?,
    )

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategoryId(
        id: Int,
        categoryId: Int?,
    )

    @Query("UPDATE transactions SET accountId = :accountId WHERE id = :id")
    suspend fun updateAccountId(
        id: Int,
        accountId: Int,
    )

    @Query("UPDATE transactions SET date = :date WHERE id = :id")
    suspend fun updateDate(
        id: Int,
        date: Long,
    )

    @Query("UPDATE transactions SET isExcluded = :isExcluded WHERE id = :id")
    suspend fun updateExclusionStatus(
        id: Int,
        isExcluded: Boolean,
    )

    @Query("UPDATE transactions SET transactionType = :transactionType WHERE id = :id")
    suspend fun updateTransactionType(
        id: Int,
        transactionType: TransactionType,
    )

    @Query("UPDATE transactions SET needsReview = 0 WHERE id = :id")
    suspend fun clearReviewFlag(id: Int)

    @Query(
        "UPDATE transactions SET isSplit = :isSplit, categoryId = CASE WHEN :isSplit = 1 THEN NULL ELSE categoryId END, description = CASE WHEN :isSplit = 1 THEN 'Split Transaction' ELSE description END WHERE id = :transactionId",
    )
    suspend fun markAsSplit(
        transactionId: Int,
        isSplit: Boolean,
    )

    @Query(
        """
        UPDATE transactions 
        SET isSplit = 0, description = :originalDescription, categoryId = :newCategoryId
        WHERE id = :transactionId
    """,
    )
    suspend fun unmarkAsSplit(
        transactionId: Int,
        originalDescription: String,
        newCategoryId: Int?,
    )

    @Insert
    suspend fun insertImage(transactionImage: TransactionImage)

    @Delete
    suspend fun deleteImage(transactionImage: TransactionImage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTagsToTransaction(crossRefs: List<TransactionTagCrossRef>)

    @Query("DELETE FROM transaction_tag_cross_ref WHERE transactionId = :transactionId")
    suspend fun clearTagsForTransaction(transactionId: Int)

    @Query("DELETE FROM transaction_tag_cross_ref")
    suspend fun deleteAllCrossRefs()

    @Query("UPDATE transactions SET sourceSmsHash = :smsHash WHERE id = :transactionId")
    suspend fun setSmsHash(
        transactionId: Int,
        smsHash: String,
    )

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun updateCategoryForIds(
        ids: List<Int>,
        categoryId: Int,
    )

    @Query("UPDATE transactions SET description = :newDescription WHERE id IN (:ids)")
    suspend fun updateDescriptionForIds(
        ids: List<Int>,
        newDescription: String,
    )

    @Query("UPDATE transactions SET accountId = :destinationAccountId WHERE accountId IN (:sourceAccountIds)")
    suspend fun reassignTransactions(
        sourceAccountIds: List<Int>,
        destinationAccountId: Int,
    )

    @Query(
        """
        INSERT INTO transaction_tag_cross_ref (transactionId, tagId)
        SELECT id, :tagId
        FROM transactions
        WHERE date BETWEEN :startDate AND :endDate
        AND id NOT IN (SELECT transactionId FROM transaction_tag_cross_ref WHERE tagId = :tagId)
    """,
    )
    suspend fun addTagForDateRange(
        tagId: Int,
        startDate: Long,
        endDate: Long,
    )

    @Query(
        """
        DELETE FROM transaction_tag_cross_ref
        WHERE tagId = :tagId
        AND transactionId IN (
            SELECT id
            FROM transactions
            WHERE date BETWEEN :startDate AND :endDate
        )
    """,
    )
    suspend fun removeTagForDateRange(
        tagId: Int,
        startDate: Long,
        endDate: Long,
    )

    @Query("DELETE FROM transaction_tag_cross_ref WHERE tagId = :tagId")
    suspend fun removeAllTransactionsForTag(tagId: Int)

    @Query("UPDATE transactions SET status = $SQL_STATUS_CONFIRMED WHERE id = :id")
    suspend fun confirmTransaction(id: Int)

    @Query("UPDATE transactions SET status = $SQL_STATUS_SKIPPED WHERE id = :id")
    suspend fun skipTransaction(id: Int)

    @Query("UPDATE transactions SET recurringRuleId = :ruleId WHERE id = :id")
    suspend fun updateRecurringRuleId(
        id: Int,
        ruleId: Int,
    )

    @Query("UPDATE transactions SET linkedTransferId = :linkedId, isExcluded = :isExcluded WHERE id = :id")
    suspend fun updateTransferLinkStatus(
        id: Int,
        linkedId: Int?,
        isExcluded: Boolean,
    )

    @Query("UPDATE transactions SET mergeDismissed = :dismissed WHERE id = :id")
    suspend fun updateMergeDismissed(
        id: Int,
        dismissed: Boolean,
    )
}

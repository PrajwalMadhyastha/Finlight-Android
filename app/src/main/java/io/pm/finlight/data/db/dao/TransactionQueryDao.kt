// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/TransactionQueryDao.kt
// REASON: REFACTOR (Domain DAO Decomposition - Issue #237) - Dedicated DAO for all
// core read operations, list fetching, detail lookups, and search queries.
// =================================================================================
package io.pm.finlight.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction as RoomTransaction
import io.pm.finlight.Tag
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionImage
import io.pm.finlight.TransactionTagCrossRef
import io.pm.finlight.TransactionType
import io.pm.finlight.TransactionWithSplits
import io.pm.finlight.data.model.MerchantPrediction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionQueryDao {
    @Query(
        """
        SELECT
            T.description,
            T.categoryId,
            T.accountId,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey
        FROM transactions AS T
        LEFT JOIN categories AS C ON T.categoryId = C.id
        LEFT JOIN accounts AS A ON T.accountId = A.id
        WHERE (LOWER(T.description) LIKE '%' || LOWER(:query) || '%' OR LOWER(T.originalDescription) LIKE '%' || LOWER(:query) || '%')
          AND T.description != ''
        GROUP BY LOWER(T.description), T.categoryId, T.accountId
        ORDER BY MAX(T.date) DESC
        LIMIT 10
    """,
    )
    fun searchMerchants(query: String): Flow<List<MerchantPrediction>>

    @Query("SELECT sourceSmsHash FROM transactions WHERE id IN (:transactionIds) AND sourceSmsHash IS NOT NULL")
    suspend fun getSmsHashesByIds(transactionIds: List<Int>): List<String>

    @RoomTransaction
    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    fun getTransactionWithSplits(transactionId: Int): Flow<TransactionWithSplits?>

    @Query("SELECT MIN(date) FROM transactions")
    fun getFirstTransactionDate(): Flow<Long?>

    @Query("SELECT * FROM transactions WHERE smsSignature IS NOT NULL AND date >= :sinceDate")
    suspend fun getTransactionsWithSignatureSince(sinceDate: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE smsSignature = :signature ORDER BY date ASC")
    suspend fun getTransactionsBySignature(signature: String): List<Transaction>

    @RoomTransaction
    @Query(
        """
        SELECT
            T.*,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey,
            (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM
            transactions AS T
        LEFT JOIN
            accounts AS A ON T.accountId = A.id
        LEFT JOIN
            categories AS C ON T.categoryId = C.id
        ORDER BY
            T.date DESC
    """,
    )
    fun getAllTransactions(): Flow<List<TransactionDetails>>

    @Query("SELECT * FROM transactions")
    fun getAllTransactionsSimple(): Flow<List<Transaction>>

    @RoomTransaction
    @Query(
        """
        WITH AtomicIncomes AS (
            SELECT T.*
            FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = $SQL_INCOME AND T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE
              AND (:accountId IS NULL OR T.accountId = :accountId)
            UNION ALL
            SELECT
                P.id, P.description, S.categoryId, S.amount, P.date, P.accountId, S.notes, P.transactionType, P.sourceSmsId, P.sourceSmsHash, P.source,
                P.originalDescription, P.isExcluded, P.smsSignature, P.originalAmount, P.currencyCode, P.conversionRate, P.isSplit, P.needsReview, P.status, P.recurringRuleId, P.mergeDismissed, P.parentReimbursementId, P.linkedTransferId
            FROM split_transactions AS S JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = $SQL_INCOME AND P.date BETWEEN :startDate AND :endDate AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE
              AND (:accountId IS NULL OR P.accountId = :accountId)
        )
        SELECT
            AI.*,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey,
            (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = AI.id) as tagNames
        FROM AtomicIncomes AS AI
        LEFT JOIN accounts AS A ON AI.accountId = A.id
        LEFT JOIN categories AS C ON AI.categoryId = C.id
        WHERE (:keyword IS NULL OR LOWER(AI.description) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(AI.notes) LIKE '%' || LOWER(:keyword) || '%')
          AND (:categoryId IS NULL OR AI.categoryId = :categoryId)
        ORDER BY AI.date DESC
    """,
    )
    fun getIncomeTransactionsForRange(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<TransactionDetails>>

    @Query("SELECT * FROM transaction_images WHERE transactionId = :transactionId")
    fun getImagesForTransaction(transactionId: Int): Flow<List<TransactionImage>>

    @RoomTransaction
    @Query(
        """
        SELECT
            T.*,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey,
            (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM
            transactions AS T
        LEFT JOIN
            accounts AS A ON T.accountId = A.id
        LEFT JOIN
            categories AS C ON T.categoryId = C.id
        WHERE T.id = :id
    """,
    )
    fun getTransactionDetailsById(id: Int): Flow<TransactionDetails?>

    @RoomTransaction
    @Query(
        """
        SELECT
            T.*,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey,
            (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM
            transactions AS T
        LEFT JOIN
            accounts AS A ON T.accountId = A.id
        LEFT JOIN
            categories AS C ON T.categoryId = C.id
        ORDER BY
            T.date DESC
        LIMIT 5
    """,
    )
    fun getRecentTransactionDetails(): Flow<List<TransactionDetails>>

    @Query("SELECT sourceSmsHash FROM transactions WHERE sourceSmsHash IS NOT NULL")
    fun getAllSmsHashes(): Flow<List<String>>

    @RoomTransaction
    @Query(
        """
        SELECT
            T.*,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey,
            (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM
            transactions AS T
        LEFT JOIN
            accounts AS A ON T.accountId = A.id
        LEFT JOIN
            categories AS C ON T.categoryId = C.id
        WHERE T.date BETWEEN :startDate AND :endDate
          AND (:keyword IS NULL OR LOWER(T.description) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(T.notes) LIKE '%' || LOWER(:keyword) || '%')
          AND (:accountId IS NULL OR T.accountId = :accountId)
          AND (:categoryId IS NULL OR T.categoryId = :categoryId)
        ORDER BY
            T.date DESC
    """,
    )
    fun getTransactionDetailsForRange(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<TransactionDetails>>

    @RoomTransaction
    @Query(
        """
        SELECT t.*, a.name as accountName, c.name as categoryName, c.iconKey as categoryIconKey, c.colorKey as categoryColorKey,
        (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = t.id) as tagNames
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        LEFT JOIN categories c ON t.categoryId = c.id
        WHERE t.accountId = :accountId
        ORDER BY t.date DESC
    """,
    )
    fun getTransactionsForAccountDetails(accountId: Int): Flow<List<TransactionDetails>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getAllTransactionsForRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getTransactionById(id: Int): Flow<Transaction?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionByIdSync(id: Int): Transaction?

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC")
    fun getTransactionsForAccount(accountId: Int): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countTransactionsForCategory(categoryId: Int): Int

    @Query("SELECT COUNT(*) FROM transaction_tag_cross_ref WHERE tagId = :tagId")
    suspend fun countTransactionsForTag(tagId: Int): Int

    @RoomTransaction
    @Query(
        "SELECT T.* FROM tags T INNER JOIN transaction_tag_cross_ref TTCR ON T.id = TTCR.tagId WHERE TTCR.transactionId = :transactionId",
    )
    fun getTagsForTransaction(transactionId: Int): Flow<List<Tag>>

    @Query(
        "SELECT T.* FROM tags T INNER JOIN transaction_tag_cross_ref TTCR ON T.id = TTCR.tagId WHERE TTCR.transactionId = :transactionId",
    )
    suspend fun getTagsForTransactionSimple(transactionId: Int): List<Tag>

    /**
     * Retrieves all transaction-tag cross-references for backup.
     */
    @Query("SELECT * FROM transaction_tag_cross_ref")
    suspend fun getAllCrossRefs(): List<TransactionTagCrossRef>

    @Query(
        """
        SELECT * FROM transactions
        WHERE sourceSmsHash IS NULL
          AND date BETWEEN :startDate AND :endDate
          AND amount BETWEEN :minAmount AND :maxAmount
          AND transactionType = :transactionType
        ORDER BY ABS(date - :smsDate) ASC
    """,
    )
    suspend fun findLinkableTransactions(
        startDate: Long,
        endDate: Long,
        minAmount: Double,
        maxAmount: Double,
        smsDate: Long,
        transactionType: TransactionType,
    ): List<Transaction>

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE (LOWER(description) = LOWER(:description) OR LOWER(originalDescription) = LOWER(:description))
        AND isExcluded = 0 AND $SQL_STATUS_ACTIVE
    """,
    )
    fun getTransactionCountForMerchant(description: String): Flow<Int>

    @Query(
        """
        SELECT * FROM transactions
        WHERE LOWER(originalDescription) = LOWER(:description)
        AND originalDescription IS NOT NULL
        AND id != :excludeId
        AND isExcluded = 0 AND $SQL_STATUS_ACTIVE
        ORDER BY date DESC
    """,
    )
    suspend fun findSimilarTransactions(
        description: String,
        excludeId: Int,
    ): List<Transaction>

    /**
     * Returns all distinct, non-null originalDescription values across all non-excluded
     * transactions. Used by the cross-account canonical nudge to find unmatched variants.
     */
    @Query("SELECT DISTINCT originalDescription FROM transactions WHERE originalDescription IS NOT NULL AND isExcluded = 0 AND $SQL_STATUS_ACTIVE")
    suspend fun getDistinctOriginalDescriptions(): List<String>

    /**
     * Returns the IDs of all non-excluded transactions whose [originalDescription] matches
     * the given value (case-insensitive). Used to bulk-update transactions for a confirmed
     * canonical nudge selection.
     */
    @Query("SELECT id FROM transactions WHERE LOWER(originalDescription) = LOWER(:originalDesc) AND isExcluded = 0 AND $SQL_STATUS_ACTIVE")
    suspend fun getTransactionIdsByOriginalDescription(originalDesc: String): List<Int>

    @RoomTransaction
    @Query(
        """
        SELECT t.*, a.name as accountName, c.name as categoryName, c.iconKey as categoryIconKey, c.colorKey as categoryColorKey,
        (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = t.id) as tagNames
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        LEFT JOIN categories c ON t.categoryId = c.id
        WHERE
            (:keyword = '' OR LOWER(t.description) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(t.notes) LIKE '%' || LOWER(:keyword) || '%') AND
            (:accountId IS NULL OR t.accountId = :accountId) AND
            (:categoryId IS NULL OR t.categoryId = :categoryId) AND
            (:transactionType IS NULL OR t.transactionType = :transactionType) AND
            (:startDate IS NULL OR t.date >= :startDate) AND
            (:endDate IS NULL OR t.date <= :endDate) AND
            (:tagId IS NULL OR t.id IN (SELECT transactionId FROM transaction_tag_cross_ref WHERE tagId = :tagId))
        ORDER BY t.date DESC
    """,
    )
    fun searchTransactions(
        keyword: String,
        accountId: Int?,
        categoryId: Int?,
        transactionType: TransactionType?,
        startDate: Long?,
        endDate: Long?,
        tagId: Int?,
    ): Flow<List<TransactionDetails>>

    @RoomTransaction
    @Query(
        """
        SELECT T.*, A.name as accountName, C.name as categoryName, C.iconKey as categoryIconKey, C.colorKey as categoryColorKey,
        (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM transactions AS T
        INNER JOIN transaction_tag_cross_ref TTCR ON T.id = TTCR.transactionId
        LEFT JOIN accounts AS A ON T.accountId = A.id
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE TTCR.tagId = :tagId
        ORDER BY T.date DESC
    """,
    )
    fun getTransactionsByTagId(tagId: Int): Flow<List<TransactionDetails>>

    @RoomTransaction
    @Query(
        """
        SELECT
            T.*,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey,
            (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM transactions AS T
        LEFT JOIN accounts AS A ON T.accountId = A.id
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE T.id IN (
            SELECT MAX(id)
            FROM transactions
            WHERE sourceSmsId IS NULL
            GROUP BY LOWER(description)
        )
        ORDER BY T.date DESC
        LIMIT :limit
    """,
    )
    fun getRecentManualTransactions(limit: Int): Flow<List<TransactionDetails>>

    /** Returns all PENDING draft transactions, ordered by date. */
    @Query("SELECT * FROM transactions WHERE status = $SQL_STATUS_PENDING ORDER BY date ASC")
    fun getPendingTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE status = $SQL_STATUS_PENDING ORDER BY date ASC")
    suspend fun getPendingTransactionsSync(): List<Transaction>

    /** Checks if a specific recurring rule already has an unconfirmed draft waiting. */
    @Query("SELECT * FROM transactions WHERE status = $SQL_STATUS_PENDING AND recurringRuleId = :ruleId LIMIT 1")
    suspend fun getPendingTransactionForRule(ruleId: Int): Transaction?

    @Query(
        """
        SELECT * FROM transactions 
        WHERE description = :merchant 
        AND accountId = :accountId 
        AND transactionType = :transactionType 
        AND mergeDismissed = 0 
        AND date >= :timeWindowStart 
        AND id != :newTxnId 
        ORDER BY date DESC LIMIT 1
        """,
    )
    suspend fun findRecentTransactionForMerge(
        merchant: String,
        accountId: Int,
        transactionType: TransactionType,
        timeWindowStart: Long,
        newTxnId: Int,
    ): Transaction?

    @Query(
        """
        SELECT * FROM transactions 
        WHERE amount = :amount 
        AND accountId != :accountId 
        AND transactionType != :transactionType
        AND date BETWEEN :startTime AND :endTime
        AND linkedTransferId IS NULL
        AND isExcluded = 0
        """,
    )
    suspend fun findPotentialTransfers(
        amount: Double,
        accountId: Int,
        transactionType: TransactionType,
        startTime: Long,
        endTime: Long,
    ): List<Transaction>
}

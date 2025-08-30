// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/TransactionDao.kt
// REASON: FIX - Corrected a build error caused by a name collision between the
// `Transaction` entity and the `@Transaction` annotation. The ambiguous import
// has been removed, and the annotation is now fully qualified (`@androidx.room.Transaction`)
// to resolve the conflict.
// =================================================================================
package io.pm.finlight

import androidx.room.*
import io.pm.finlight.data.model.MerchantPrediction
import kotlinx.coroutines.flow.Flow
// Removed the ambiguous import: import androidx.room.Transaction

@Dao
interface TransactionDao {

    @Query("""
        SELECT
            T.description,
            T.categoryId,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey
        FROM transactions AS T
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE (LOWER(T.description) LIKE '%' || LOWER(:query) || '%' OR LOWER(T.originalDescription) LIKE '%' || LOWER(:query) || '%')
          AND T.description != ''
        GROUP BY LOWER(T.description), T.categoryId
        ORDER BY MAX(T.date) DESC
        LIMIT 10
    """)
    fun searchMerchants(query: String): Flow<List<MerchantPrediction>>

    @Query("DELETE FROM transactions WHERE id IN (:transactionIds)")
    suspend fun deleteByIds(transactionIds: List<Int>)

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    fun getTransactionWithSplits(transactionId: Int): Flow<TransactionWithSplits?>

    @Query("UPDATE transactions SET isSplit = :isSplit, categoryId = CASE WHEN :isSplit = 1 THEN NULL ELSE categoryId END, description = CASE WHEN :isSplit = 1 THEN 'Split Transaction' ELSE description END WHERE id = :transactionId")
    suspend fun markAsSplit(transactionId: Int, isSplit: Boolean)

    @Query("""
        UPDATE transactions 
        SET isSplit = 0, description = :originalDescription, categoryId = :newCategoryId
        WHERE id = :transactionId
    """)
    suspend fun unmarkAsSplit(transactionId: Int, originalDescription: String, newCategoryId: Int?)


    @Query("SELECT MIN(date) FROM transactions")
    fun getFirstTransactionDate(): Flow<Long?>

    @Query("SELECT * FROM transactions WHERE smsSignature IS NOT NULL AND date >= :sinceDate")
    suspend fun getTransactionsWithSignatureSince(sinceDate: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE smsSignature = :signature ORDER BY date ASC")
    suspend fun getTransactionsBySignature(signature: String): List<Transaction>


    @Query(
        """
        WITH AtomicExpenses AS (
            -- 1. Regular, non-split transactions
            SELECT T.categoryId, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = 'expense' AND T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0
            UNION ALL
            -- 2. Child items from split transactions
            SELECT S.categoryId, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = 'expense' AND P.date BETWEEN :startDate AND :endDate AND P.isExcluded = 0
        )
        SELECT C.name as categoryName, SUM(AE.amount) as totalAmount, C.iconKey as iconKey, C.colorKey as colorKey
        FROM AtomicExpenses AS AE
        JOIN categories AS C ON AE.categoryId = C.id
        WHERE AE.categoryId IS NOT NULL
        GROUP BY C.name
        ORDER BY totalAmount DESC
        LIMIT 3
    """
    )
    suspend fun getTopSpendingCategoriesForRange(startDate: Long, endDate: Long): List<CategorySpending>

    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT T.categoryId, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = 'expense' AND T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0
            UNION ALL
            SELECT S.categoryId, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = 'expense' AND P.date BETWEEN :startDate AND :endDate AND P.isExcluded = 0
        )
        SELECT C.name as categoryName, SUM(AE.amount) as totalAmount, C.iconKey as iconKey, C.colorKey as colorKey
        FROM AtomicExpenses AS AE
        JOIN categories AS C ON AE.categoryId = C.id
        WHERE AE.categoryId IS NOT NULL
        GROUP BY C.name
        ORDER BY totalAmount DESC
        LIMIT 1
    """
    )
    fun getTopSpendingCategoriesForRangeFlow(startDate: Long, endDate: Long): Flow<CategorySpending?>


    @Query("UPDATE transactions SET isExcluded = :isExcluded WHERE id = :id")
    suspend fun updateExclusionStatus(id: Int, isExcluded: Boolean)

    @androidx.room.Transaction
    @Query(
        """
        SELECT
            T.*,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey
        FROM
            transactions AS T
        LEFT JOIN
            accounts AS A ON T.accountId = A.id
        LEFT JOIN
            categories AS C ON T.categoryId = C.id
        ORDER BY
            T.date DESC
    """
    )
    fun getAllTransactions(): Flow<List<TransactionDetails>>

    @androidx.room.Transaction
    @Query("""
        WITH AtomicIncomes AS (
            SELECT T.*
            FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = 'income' AND T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0
            UNION ALL
            SELECT
                P.id, P.description, S.categoryId, S.amount, P.date, P.accountId, S.notes, P.transactionType, P.sourceSmsId, P.sourceSmsHash, P.source,
                P.originalDescription, P.isExcluded, P.smsSignature, P.originalAmount, P.currencyCode, P.conversionRate, P.isSplit
            FROM split_transactions AS S JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = 'income' AND P.date BETWEEN :startDate AND :endDate AND P.isExcluded = 0
        )
        SELECT
            AI.*,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey
        FROM AtomicIncomes AS AI
        LEFT JOIN accounts AS A ON AI.accountId = A.id
        LEFT JOIN categories AS C ON AI.categoryId = C.id
        WHERE (:keyword IS NULL OR LOWER(AI.description) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(AI.notes) LIKE '%' || LOWER(:keyword) || '%')
          AND (:accountId IS NULL OR AI.accountId = :accountId)
          AND (:categoryId IS NULL OR AI.categoryId = :categoryId)
        ORDER BY AI.date DESC
    """)
    fun getIncomeTransactionsForRange(startDate: Long, endDate: Long, keyword: String?, accountId: Int?, categoryId: Int?): Flow<List<TransactionDetails>>

    @Query("""
        WITH AtomicIncomes AS (
            SELECT T.categoryId, T.amount, T.description, T.notes, T.accountId
            FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = 'income' AND T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0
            UNION ALL
            SELECT S.categoryId, S.amount, P.description, S.notes, P.accountId
            FROM split_transactions AS S JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = 'income' AND P.date BETWEEN :startDate AND :endDate AND P.isExcluded = 0
        )
        SELECT 
            C.name as categoryName, 
            SUM(AI.amount) as totalAmount,
            C.iconKey as iconKey,
            C.colorKey as colorKey
        FROM AtomicIncomes AS AI
        JOIN categories AS C ON AI.categoryId = C.id
        WHERE AI.categoryId IS NOT NULL
          AND (:keyword IS NULL OR LOWER(AI.description) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(AI.notes) LIKE '%' || LOWER(:keyword) || '%')
          AND (:accountId IS NULL OR AI.accountId = :accountId)
          AND (:categoryId IS NULL OR C.id = :categoryId)
        GROUP BY C.name
        ORDER BY totalAmount DESC
    """)
    fun getIncomeByCategoryForMonth(startDate: Long, endDate: Long, keyword: String?, accountId: Int?, categoryId: Int?): Flow<List<CategorySpending>>

    @Query("""
        SELECT
            T.description as merchantName,
            SUM(T.amount) as totalAmount,
            COUNT(T.id) as transactionCount
        FROM transactions AS T
        WHERE T.transactionType = 'expense' AND T.date BETWEEN :startDate AND :endDate
          AND T.isExcluded = 0
          AND T.isSplit = 0
          AND (:keyword IS NULL OR LOWER(T.description) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(T.notes) LIKE '%' || LOWER(:keyword) || '%')
          AND (:accountId IS NULL OR T.accountId = :accountId)
          AND (:categoryId IS NULL OR T.categoryId = :categoryId)
        GROUP BY LOWER(T.description)
        ORDER BY totalAmount DESC
    """)
    fun getSpendingByMerchantForMonth(startDate: Long, endDate: Long, keyword: String?, accountId: Int?, categoryId: Int?): Flow<List<MerchantSpendingSummary>>

    @androidx.room.Transaction
    @Query("""
        SELECT T.*, A.name as accountName, C.name as categoryName, C.iconKey as categoryIconKey, C.colorKey as categoryColorKey
        FROM transactions AS T
        LEFT JOIN accounts AS A ON T.accountId = A.id
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE C.name = :categoryName AND T.date BETWEEN :startDate AND :endDate
        ORDER BY T.date DESC
    """)
    fun getTransactionsForCategoryName(categoryName: String, startDate: Long, endDate: Long): Flow<List<TransactionDetails>>

    @androidx.room.Transaction
    @Query("""
        SELECT T.*, A.name as accountName, C.name as categoryName, C.iconKey as categoryIconKey, C.colorKey as categoryColorKey
        FROM transactions AS T
        LEFT JOIN accounts AS A ON T.accountId = A.id
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE T.description = :merchantName AND T.date BETWEEN :startDate AND :endDate
        ORDER BY T.date DESC
    """)
    fun getTransactionsForMerchantName(merchantName: String, startDate: Long, endDate: Long): Flow<List<TransactionDetails>>

    @Query("""
        WITH AtomicExpenses AS (
            SELECT P.date, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            JOIN categories AS C ON S.categoryId = C.id
            WHERE P.transactionType = 'expense' AND P.isExcluded = 0 AND C.name = :categoryName
            UNION ALL
            SELECT T.date, T.amount FROM transactions AS T
            JOIN categories AS C ON T.categoryId = C.id
            WHERE T.isSplit = 0 AND T.transactionType = 'expense' AND T.isExcluded = 0 AND C.name = :categoryName
        )
        SELECT
            strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') as period,
            SUM(amount) as totalAmount
        FROM AtomicExpenses
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY period
        ORDER BY period ASC
    """)
    fun getMonthlySpendingForCategory(categoryName: String, startDate: Long, endDate: Long): Flow<List<PeriodTotal>>

    @Query("""
        WITH AtomicExpenses AS (
            SELECT P.date, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = 'expense' AND P.isExcluded = 0 AND P.description = :merchantName
            UNION ALL
            SELECT T.date, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = 'expense' AND T.isExcluded = 0 AND T.description = :merchantName
        )
        SELECT
            strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') as period,
            SUM(amount) as totalAmount
        FROM AtomicExpenses
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY period
        ORDER BY period ASC
    """)
    fun getMonthlySpendingForMerchant(merchantName: String, startDate: Long, endDate: Long): Flow<List<PeriodTotal>>


    @Insert
    suspend fun insertImage(transactionImage: TransactionImage)

    @Delete
    suspend fun deleteImage(transactionImage: TransactionImage)

    @Query("SELECT * FROM transaction_images WHERE transactionId = :transactionId")
    fun getImagesForTransaction(transactionId: Int): Flow<List<TransactionImage>>


    @Query("UPDATE transactions SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: Int, description: String)

    @Query("UPDATE transactions SET amount = :amount WHERE id = :id")
    suspend fun updateAmount(id: Int, amount: Double)

    @Query("UPDATE transactions SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Int, notes: String?)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategoryId(id: Int, categoryId: Int?)

    @Query("UPDATE transactions SET accountId = :accountId WHERE id = :id")
    suspend fun updateAccountId(id: Int, accountId: Int)

    @Query("UPDATE transactions SET date = :date WHERE id = :id")
    suspend fun updateDate(id: Int, date: Long)


    @androidx.room.Transaction
    @Query(
        """
        SELECT
            T.*,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey
        FROM
            transactions AS T
        LEFT JOIN
            accounts AS A ON T.accountId = A.id
        LEFT JOIN
            categories AS C ON T.categoryId = C.id
        WHERE T.id = :id
    """
    )
    fun getTransactionDetailsById(id: Int): Flow<TransactionDetails?>


    @androidx.room.Transaction
    @Query(
        """
        SELECT
            T.*,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey
        FROM
            transactions AS T
        LEFT JOIN
            accounts AS A ON T.accountId = A.id
        LEFT JOIN
            categories AS C ON T.categoryId = C.id
        ORDER BY
            T.date DESC
        LIMIT 5
    """
    )
    fun getRecentTransactionDetails(): Flow<List<TransactionDetails>>

    @Query("SELECT sourceSmsHash FROM transactions WHERE sourceSmsHash IS NOT NULL")
    fun getAllSmsHashes(): Flow<List<String>>

    @androidx.room.Transaction
    @Query(
        """
        SELECT
            T.*,
            A.name as accountName,
            C.name as categoryName,
            C.iconKey as categoryIconKey,
            C.colorKey as categoryColorKey
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
    """
    )
    fun getTransactionDetailsForRange(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?
    ): Flow<List<TransactionDetails>>

    @androidx.room.Transaction
    @Query(
        """
        SELECT t.*, a.name as accountName, c.name as categoryName, c.iconKey as categoryIconKey, c.colorKey as categoryColorKey
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        LEFT JOIN categories c ON t.categoryId = c.id
        WHERE t.accountId = :accountId
        ORDER BY t.date DESC
    """
    )
    fun getTransactionsForAccountDetails(accountId: Int): Flow<List<TransactionDetails>>

    @Query("SELECT * FROM transactions")
    fun getAllTransactionsSimple(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getAllTransactionsForRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getTransactionById(id: Int): Flow<Transaction?>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC")
    fun getTransactionsForAccount(accountId: Int): Flow<List<Transaction>>

    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT T.categoryId, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = 'expense' AND T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0
            UNION ALL
            SELECT S.categoryId, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = 'expense' AND P.date BETWEEN :startDate AND :endDate AND P.isExcluded = 0
        )
        SELECT SUM(AE.amount) FROM AtomicExpenses AS AE
        JOIN categories AS C ON AE.categoryId = C.id
        WHERE C.name = :categoryName AND AE.categoryId IS NOT NULL
    """
    )
    fun getSpendingForCategory(
        categoryName: String,
        startDate: Long,
        endDate: Long,
    ): Flow<Double?>

    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT T.categoryId, T.amount, T.description, T.notes, T.accountId
            FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = 'expense' AND T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0
            UNION ALL
            SELECT S.categoryId, S.amount, P.description, S.notes, P.accountId
            FROM split_transactions AS S JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = 'expense' AND P.date BETWEEN :startDate AND :endDate AND P.isExcluded = 0
        )
        SELECT 
            C.name as categoryName, 
            SUM(AE.amount) as totalAmount,
            C.iconKey as iconKey,
            C.colorKey as colorKey
        FROM AtomicExpenses AS AE
        JOIN categories AS C ON AE.categoryId = C.id
        WHERE AE.categoryId IS NOT NULL
          AND (:keyword IS NULL OR LOWER(AE.description) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(AE.notes) LIKE '%' || LOWER(:keyword) || '%')
          AND (:accountId IS NULL OR AE.accountId = :accountId)
          AND (:categoryId IS NULL OR AE.categoryId = :categoryId)
        GROUP BY C.name
        ORDER BY totalAmount DESC
    """
    )
    fun getSpendingByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?
    ): Flow<List<CategorySpending>>

    @Query(
        """
        SELECT
            strftime('%Y-%m', T1.date / 1000, 'unixepoch', 'localtime') as monthYear,
            SUM(CASE WHEN T1.transactionType = 'income' AND T1.isSplit = 0 THEN T1.amount ELSE 0 END) + 
            (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE strftime('%Y-%m', p.date / 1000, 'unixepoch', 'localtime') = strftime('%Y-%m', T1.date / 1000, 'unixepoch', 'localtime') AND p.isExcluded = 0 AND p.transactionType = 'income') as totalIncome,
            SUM(CASE WHEN T1.transactionType = 'expense' AND T1.isSplit = 0 THEN T1.amount ELSE 0 END) + 
            (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE strftime('%Y-%m', p.date / 1000, 'unixepoch', 'localtime') = strftime('%Y-%m', T1.date / 1000, 'unixepoch', 'localtime') AND p.isExcluded = 0 AND p.transactionType = 'expense') as totalExpenses
        FROM transactions AS T1
        WHERE T1.date >= :startDate AND T1.isExcluded = 0
        GROUP BY monthYear
        ORDER BY monthYear ASC
    """
    )
    fun getMonthlyTrends(startDate: Long): Flow<List<MonthlyTrend>>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countTransactionsForCategory(categoryId: Int): Int

    @Query("SELECT COUNT(*) FROM transaction_tag_cross_ref WHERE tagId = :tagId")
    suspend fun countTransactionsForTag(tagId: Int): Int

    @Query("""
        SELECT
            SUM(CASE WHEN T.transactionType = 'income' AND T.isSplit = 0 THEN T.amount ELSE 0 END) + (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE p.date BETWEEN :startDate AND :endDate AND p.transactionType = 'income' AND p.isExcluded = 0) as totalIncome,
            SUM(CASE WHEN T.transactionType = 'expense' AND T.isSplit = 0 THEN T.amount ELSE 0 END) + (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE p.date BETWEEN :startDate AND :endDate AND p.transactionType = 'expense' AND p.isExcluded = 0) as totalExpenses
        FROM transactions AS T
        WHERE T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0
    """)
    suspend fun getFinancialSummaryForRange(startDate: Long, endDate: Long): FinancialSummary?

    @Query("""
        SELECT
            SUM(CASE WHEN T.transactionType = 'income' AND T.isSplit = 0 THEN T.amount ELSE 0 END) + (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE p.date BETWEEN :startDate AND :endDate AND p.transactionType = 'income' AND p.isExcluded = 0) as totalIncome,
            SUM(CASE WHEN T.transactionType = 'expense' AND T.isSplit = 0 THEN T.amount ELSE 0 END) + (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE p.date BETWEEN :startDate AND :endDate AND p.transactionType = 'expense' AND p.isExcluded = 0) as totalExpenses
        FROM transactions AS T
        WHERE T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0
    """)
    fun getFinancialSummaryForRangeFlow(startDate: Long, endDate: Long): Flow<FinancialSummary?>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTagsToTransaction(crossRefs: List<TransactionTagCrossRef>)

    @Query("DELETE FROM transaction_tag_cross_ref WHERE transactionId = :transactionId")
    suspend fun clearTagsForTransaction(transactionId: Int)

    @androidx.room.Transaction
    @Query("SELECT T.* FROM tags T INNER JOIN transaction_tag_cross_ref TTCR ON T.id = TTCR.tagId WHERE TTCR.transactionId = :transactionId")
    fun getTagsForTransaction(transactionId: Int): Flow<List<Tag>>

    @Query("SELECT T.* FROM tags T INNER JOIN transaction_tag_cross_ref TTCR ON T.id = TTCR.tagId WHERE TTCR.transactionId = :transactionId")
    suspend fun getTagsForTransactionSimple(transactionId: Int): List<Tag>

    @Query("""
        WITH AtomicExpenses AS (
            SELECT T.date, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = 'expense' AND T.isExcluded = 0
            UNION ALL
            SELECT P.date, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = 'expense' AND P.isExcluded = 0
        )
        SELECT
            strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') as date,
            SUM(amount) as totalAmount
        FROM AtomicExpenses
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date ASC
    """)
    fun getDailySpendingForDateRange(startDate: Long, endDate: Long): Flow<List<DailyTotal>>

    @Query("""
        WITH AtomicExpenses AS (
            SELECT P.date, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = 'expense' AND P.isExcluded = 0
            UNION ALL
            SELECT T.date, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = 'expense' AND T.isExcluded = 0
        )
        SELECT
            strftime('%Y-%W', date / 1000, 'unixepoch', 'localtime') as period,
            SUM(amount) as totalAmount
        FROM AtomicExpenses
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY period
        ORDER BY period ASC
    """)
    fun getWeeklySpendingForDateRange(startDate: Long, endDate: Long): Flow<List<PeriodTotal>>

    @Query("""
        WITH AtomicExpenses AS (
            SELECT P.date, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = 'expense' AND P.isExcluded = 0
            UNION ALL
            SELECT T.date, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = 'expense' AND T.isExcluded = 0
        )
        SELECT
            strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') as period,
            SUM(amount) as totalAmount
        FROM AtomicExpenses
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY period
        ORDER BY period ASC
    """)
    fun getMonthlySpendingForDateRange(startDate: Long, endDate: Long): Flow<List<PeriodTotal>>

    @Query("UPDATE transactions SET sourceSmsHash = :smsHash WHERE id = :transactionId")
    suspend fun setSmsHash(transactionId: Int, smsHash: String)

    @Query("""
        SELECT * FROM transactions
        WHERE sourceSmsHash IS NULL
          AND date BETWEEN :startDate AND :endDate
          AND amount BETWEEN :minAmount AND :maxAmount
          AND transactionType = :transactionType
        ORDER BY ABS(date - :smsDate) ASC
    """)
    suspend fun findLinkableTransactions(
        startDate: Long,
        endDate: Long,
        minAmount: Double,
        maxAmount: Double,
        smsDate: Long,
        transactionType: String
    ): List<Transaction>

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE LOWER(description) = LOWER(:description) OR LOWER(originalDescription) = LOWER(:description)
        AND isExcluded = 0
    """)
    fun getTransactionCountForMerchant(description: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE (LOWER(description) = LOWER(:description) OR LOWER(originalDescription) = LOWER(:description))
        AND isExcluded = 0
    """)
    suspend fun getTransactionCountForMerchantSuspend(description: String): Int

    @Query("""
        SELECT * FROM transactions
        WHERE (LOWER(description) = LOWER(:description) OR LOWER(originalDescription) = LOWER(:description))
        AND id != :excludeId
        AND isExcluded = 0
    """)
    suspend fun findSimilarTransactions(description: String, excludeId: Int): List<Transaction>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun updateCategoryForIds(ids: List<Int>, categoryId: Int)

    @Query("UPDATE transactions SET description = :newDescription WHERE id IN (:ids)")
    suspend fun updateDescriptionForIds(ids: List<Int>, newDescription: String)

    @androidx.room.Transaction
    @Query("""
        SELECT t.*, a.name as accountName, c.name as categoryName, c.iconKey as categoryIconKey, c.colorKey as categoryColorKey
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        LEFT JOIN categories c ON t.categoryId = c.id
        WHERE
            (:keyword = '' OR LOWER(t.description) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(t.notes) LIKE '%' || LOWER(:keyword) || '%') AND
            (:accountId IS NULL OR t.accountId = :accountId) AND
            (:categoryId IS NULL OR t.categoryId = :categoryId) AND
            (:transactionType IS NULL OR t.transactionType = :transactionType) AND
            (:startDate IS NULL OR t.date >= :startDate) AND
            (:endDate IS NULL OR t.date <= :endDate)
        ORDER BY t.date DESC
    """)
    fun searchTransactions(
        keyword: String,
        accountId: Int?,
        categoryId: Int?,
        transactionType: String?,
        startDate: Long?,
        endDate: Long?,
    ): Flow<List<TransactionDetails>>
}

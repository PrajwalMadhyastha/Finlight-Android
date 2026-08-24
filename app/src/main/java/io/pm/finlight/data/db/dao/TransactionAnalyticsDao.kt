// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/TransactionAnalyticsDao.kt
// REASON: REFACTOR (Domain DAO Decomposition - Issue #237) - Dedicated DAO for all
// complex aggregations, groupings, monthly totals, trends, and spending analysis.
// =================================================================================
package io.pm.finlight.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction as RoomTransaction
import io.pm.finlight.CategorySpending
import io.pm.finlight.DailyTotal
import io.pm.finlight.DailyTrend
import io.pm.finlight.FinancialSummary
import io.pm.finlight.MerchantSpendingSummary
import io.pm.finlight.MonthlyTrend
import io.pm.finlight.PeriodTotal
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.WeeklyTrend
import io.pm.finlight.data.model.SpendingAnalysisItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionAnalyticsDao {
    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = $SQL_EXPENSE AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE AND T.date BETWEEN :startDate AND :endDate
            UNION ALL
            SELECT S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = $SQL_EXPENSE AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE AND P.date BETWEEN :startDate AND :endDate
        )
        SELECT SUM(AE.amount) / (CAST((:endDate - :startDate) AS REAL) / 86400000.0)
        FROM AtomicExpenses AS AE
    """,
    )
    suspend fun getAverageDailySpendingForRange(
        startDate: Long,
        endDate: Long,
    ): Double?

    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = $SQL_EXPENSE AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE AND T.date >= :startDate
            UNION ALL
            SELECT S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = $SQL_EXPENSE AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE AND P.date >= :startDate
        )
        SELECT SUM(AE.amount) FROM AtomicExpenses AS AE
    """,
    )
    suspend fun getTotalExpensesSince(startDate: Long): Double?

    @Query(
        "SELECT DISTINCT description FROM transactions WHERE transactionType = $SQL_EXPENSE AND isExcluded = 0 AND $SQL_STATUS_ACTIVE AND description IS NOT NULL ORDER BY description ASC",
    )
    fun getAllExpenseMerchants(): Flow<List<String>>

    @Query(
        """
        WITH AllExpenses AS (
            SELECT T.id, T.categoryId, T.amount, T.description
            FROM transactions T
            WHERE T.isSplit = 0 AND (:transactionType IS NULL OR T.transactionType = :transactionType) AND T.date BETWEEN :startDate AND :endDate AND (:includeExcluded = 1 OR T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE) AND $SQL_T_STATUS_ACTIVE
            UNION ALL
            SELECT P.id, S.categoryId, S.amount, P.description
            FROM split_transactions S
            JOIN transactions P ON S.parentTransactionId = P.id
            WHERE (:transactionType IS NULL OR P.transactionType = :transactionType) AND P.date BETWEEN :startDate AND :endDate AND (:includeExcluded = 1 OR P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE) AND $SQL_P_STATUS_ACTIVE
        )
        SELECT
            C.id as dimensionId,
            C.name as dimensionName,
            SUM(AE.amount) as totalAmount,
            COUNT(AE.id) as transactionCount
        FROM AllExpenses AE
        JOIN categories C ON AE.categoryId = C.id
        WHERE AE.categoryId IS NOT NULL
          AND (:searchQuery IS NULL OR C.name LIKE '%' || :searchQuery || '%')
          AND (:filterMerchantName IS NULL OR AE.description = :filterMerchantName)
          AND (:filterTagId IS NULL OR EXISTS (
              SELECT 1 FROM transaction_tag_cross_ref ttcr
              WHERE ttcr.transactionId = AE.id AND ttcr.tagId = :filterTagId
          ))
          AND (:filterCategoryId IS NULL OR C.id = :filterCategoryId)
        GROUP BY C.id, C.name
        ORDER BY totalAmount DESC
    """,
    )
    fun getSpendingAnalysisByCategory(
        startDate: Long,
        endDate: Long,
        filterTagId: Int?,
        filterMerchantName: String?,
        filterCategoryId: Int?,
        searchQuery: String?,
        includeExcluded: Boolean,
        transactionType: TransactionType?,
    ): Flow<List<SpendingAnalysisItem>>

    @Query(
        """
        WITH AllExpenses AS (
            SELECT T.id, T.categoryId, T.amount, T.description
            FROM transactions T
            WHERE T.isSplit = 0 AND (:transactionType IS NULL OR T.transactionType = :transactionType) AND T.date BETWEEN :startDate AND :endDate AND (:includeExcluded = 1 OR T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE) AND $SQL_T_STATUS_ACTIVE
            UNION ALL
            SELECT P.id, S.categoryId, S.amount, P.description
            FROM split_transactions S
            JOIN transactions P ON S.parentTransactionId = P.id
            WHERE (:transactionType IS NULL OR P.transactionType = :transactionType) AND P.date BETWEEN :startDate AND :endDate AND (:includeExcluded = 1 OR P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE) AND $SQL_P_STATUS_ACTIVE
        )
        SELECT
            TG.id AS dimensionId,
            TG.name AS dimensionName,
            SUM(AE.amount) AS totalAmount,
            COUNT(AE.id) AS transactionCount
        FROM AllExpenses AE
        JOIN transaction_tag_cross_ref TTCR ON AE.id = TTCR.transactionId
        JOIN tags TG ON TTCR.tagId = TG.id
        WHERE
            (:searchQuery IS NULL OR TG.name LIKE '%' || :searchQuery || '%')
            AND (:filterCategoryId IS NULL OR AE.categoryId = :filterCategoryId)
            AND (:filterMerchantName IS NULL OR AE.description = :filterMerchantName)
            AND (:filterTagId IS NULL OR TG.id = :filterTagId)
        GROUP BY TG.id, TG.name
        ORDER BY totalAmount DESC
    """,
    )
    fun getSpendingAnalysisByTag(
        startDate: Long,
        endDate: Long,
        filterCategoryId: Int?,
        filterMerchantName: String?,
        filterTagId: Int?,
        searchQuery: String?,
        includeExcluded: Boolean,
        transactionType: TransactionType?,
    ): Flow<List<SpendingAnalysisItem>>

    @Query(
        """
        WITH AllExpenses AS (
            SELECT T.id, T.categoryId, T.amount, T.description
            FROM transactions T
            WHERE T.isSplit = 0 AND (:transactionType IS NULL OR T.transactionType = :transactionType) AND T.date BETWEEN :startDate AND :endDate AND (:includeExcluded = 1 OR T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE) AND $SQL_T_STATUS_ACTIVE
            UNION ALL
            SELECT P.id, S.categoryId, S.amount, P.description
            FROM split_transactions S
            JOIN transactions P ON S.parentTransactionId = P.id
            WHERE (:transactionType IS NULL OR P.transactionType = :transactionType) AND P.date BETWEEN :startDate AND :endDate AND (:includeExcluded = 1 OR P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE) AND $SQL_P_STATUS_ACTIVE
        )
        SELECT
            LOWER(AE.description) as dimensionId,
            MIN(AE.description) as dimensionName,
            SUM(AE.amount) as totalAmount,
            COUNT(AE.id) as transactionCount
        FROM AllExpenses AE
        WHERE
            AE.description IS NOT NULL
            AND (:searchQuery IS NULL OR AE.description LIKE '%' || :searchQuery || '%')
            AND (:filterCategoryId IS NULL OR AE.categoryId = :filterCategoryId)
            AND (:filterTagId IS NULL OR EXISTS (
                SELECT 1 FROM transaction_tag_cross_ref ttcr
                WHERE ttcr.transactionId = AE.id AND ttcr.tagId = :filterTagId
            ))
            AND (:filterMerchantName IS NULL OR AE.description = :filterMerchantName)
        GROUP BY dimensionId
        ORDER BY totalAmount DESC
    """,
    )
    fun getSpendingAnalysisByMerchant(
        startDate: Long,
        endDate: Long,
        filterCategoryId: Int?,
        filterTagId: Int?,
        filterMerchantName: String?,
        searchQuery: String?,
        includeExcluded: Boolean,
        transactionType: TransactionType?,
    ): Flow<List<SpendingAnalysisItem>>

    @RoomTransaction
    @Query(
        """
        SELECT T.*, A.name as accountName, C.name as categoryName, C.iconKey as categoryIconKey, C.colorKey as categoryColorKey,
        (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM transactions AS T
        LEFT JOIN accounts AS A ON T.accountId = A.id
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE T.categoryId = :categoryId AND T.date BETWEEN :startDate AND :endDate
        ORDER BY T.date DESC
    """,
    )
    fun getTransactionsForCategoryInRange(
        categoryId: Int,
        startDate: Long,
        endDate: Long,
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
        WHERE TTCR.tagId = :tagId AND T.date BETWEEN :startDate AND :endDate
        ORDER BY T.date DESC
    """,
    )
    fun getTransactionsForTagInRange(
        tagId: Int,
        startDate: Long,
        endDate: Long,
    ): Flow<List<TransactionDetails>>

    @RoomTransaction
    @Query(
        """
        SELECT T.*, A.name as accountName, C.name as categoryName, C.iconKey as categoryIconKey, C.colorKey as categoryColorKey,
        (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM transactions AS T
        LEFT JOIN accounts AS A ON T.accountId = A.id
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE LOWER(T.description) = :merchantName AND T.date BETWEEN :startDate AND :endDate
        ORDER BY T.date DESC
    """,
    )
    fun getTransactionsForMerchantInRange(
        merchantName: String,
        startDate: Long,
        endDate: Long,
    ): Flow<List<TransactionDetails>>

    @Query(
        """
        WITH AtomicExpenses AS (
            -- 1. Regular, non-split transactions
            SELECT T.categoryId, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = $SQL_EXPENSE AND T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE
            UNION ALL
            -- 2. Child items from split transactions
            SELECT S.categoryId, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = $SQL_EXPENSE AND P.date BETWEEN :startDate AND :endDate AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE
        )
        SELECT C.name as categoryName, SUM(AE.amount) as totalAmount, C.iconKey as iconKey, C.colorKey as colorKey
        FROM AtomicExpenses AS AE
        JOIN categories AS C ON AE.categoryId = C.id
        WHERE AE.categoryId IS NOT NULL
        GROUP BY C.name
        ORDER BY totalAmount DESC
        LIMIT 3
    """,
    )
    suspend fun getTopSpendingCategoriesForRange(
        startDate: Long,
        endDate: Long,
    ): List<CategorySpending>

    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT T.categoryId, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = $SQL_EXPENSE AND T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE
            UNION ALL
            SELECT S.categoryId, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = $SQL_EXPENSE AND P.date BETWEEN :startDate AND :endDate AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE
        )
        SELECT C.name as categoryName, SUM(AE.amount) as totalAmount, C.iconKey as iconKey, C.colorKey as colorKey
        FROM AtomicExpenses AS AE
        JOIN categories AS C ON AE.categoryId = C.id
        WHERE AE.categoryId IS NOT NULL
        GROUP BY C.name
        ORDER BY totalAmount DESC
        LIMIT 1
    """,
    )
    fun getTopSpendingCategoriesForRangeFlow(
        startDate: Long,
        endDate: Long,
    ): Flow<CategorySpending?>

    @Query(
        """
        WITH AtomicIncomes AS (
            SELECT T.categoryId, T.amount, T.description, T.notes, T.accountId
            FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = $SQL_INCOME AND T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE
              AND (:accountId IS NULL OR T.accountId = :accountId)
            UNION ALL
            SELECT S.categoryId, S.amount, P.description, S.notes, P.accountId
            FROM split_transactions AS S JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = $SQL_INCOME AND P.date BETWEEN :startDate AND :endDate AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE
              AND (:accountId IS NULL OR P.accountId = :accountId)
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
          AND (:categoryId IS NULL OR C.id = :categoryId)
        GROUP BY C.name
        ORDER BY totalAmount DESC
    """,
    )
    fun getIncomeByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
    ): Flow<List<CategorySpending>>

    @Query(
        """
        SELECT
            T.description as merchantName,
            SUM(T.amount) as totalAmount,
            COUNT(T.id) as transactionCount
        FROM transactions AS T
        WHERE (:transactionType IS NULL OR T.transactionType = :transactionType) AND T.date BETWEEN :startDate AND :endDate
          AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE
          AND T.isSplit = 0
          AND (:keyword IS NULL OR LOWER(T.description) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(T.notes) LIKE '%' || LOWER(:keyword) || '%')
          AND (:accountId IS NULL OR T.accountId = :accountId)
          AND (:categoryId IS NULL OR T.categoryId = :categoryId)
        GROUP BY LOWER(T.description)
        ORDER BY totalAmount DESC
    """,
    )
    fun getSpendingByMerchantForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
        transactionType: TransactionType?,
    ): Flow<List<MerchantSpendingSummary>>

    @RoomTransaction
    @Query(
        """
        SELECT T.*, A.name as accountName, C.name as categoryName, C.iconKey as categoryIconKey, C.colorKey as categoryColorKey,
        (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM transactions AS T
        LEFT JOIN accounts AS A ON T.accountId = A.id
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE C.name = :categoryName AND T.date BETWEEN :startDate AND :endDate
        ORDER BY T.date DESC
    """,
    )
    fun getTransactionsForCategoryName(
        categoryName: String,
        startDate: Long,
        endDate: Long,
    ): Flow<List<TransactionDetails>>

    @RoomTransaction
    @Query(
        """
        SELECT T.*, A.name as accountName, C.name as categoryName, C.iconKey as categoryIconKey, C.colorKey as categoryColorKey,
        (SELECT GROUP_CONCAT(Tag.name, ', ') FROM tags AS Tag INNER JOIN transaction_tag_cross_ref AS TTCR ON Tag.id = TTCR.tagId WHERE TTCR.transactionId = T.id) as tagNames
        FROM transactions AS T
        LEFT JOIN accounts AS A ON T.accountId = A.id
        LEFT JOIN categories AS C ON T.categoryId = C.id
        WHERE T.description = :merchantName AND T.date BETWEEN :startDate AND :endDate
        ORDER BY T.date DESC
    """,
    )
    fun getTransactionsForMerchantName(
        merchantName: String,
        startDate: Long,
        endDate: Long,
    ): Flow<List<TransactionDetails>>

    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT P.date, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            JOIN categories AS C ON S.categoryId = C.id
            WHERE P.transactionType = $SQL_EXPENSE AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE AND C.name = :categoryName
            UNION ALL
            SELECT T.date, T.amount FROM transactions AS T
            JOIN categories AS C ON T.categoryId = C.id
            WHERE T.isSplit = 0 AND T.transactionType = $SQL_EXPENSE AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE AND C.name = :categoryName
        )
        SELECT
            strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') as period,
            SUM(amount) as totalAmount
        FROM AtomicExpenses
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY period
        ORDER BY period ASC
    """,
    )
    fun getMonthlySpendingForCategory(
        categoryName: String,
        startDate: Long,
        endDate: Long,
    ): Flow<List<PeriodTotal>>

    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT P.date, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = $SQL_EXPENSE AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE AND P.description = :merchantName
            UNION ALL
            SELECT T.date, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = $SQL_EXPENSE AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE AND T.description = :merchantName
        )
        SELECT
            strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') as period,
            SUM(amount) as totalAmount
        FROM AtomicExpenses
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY period
        ORDER BY period ASC
    """,
    )
    fun getMonthlySpendingForMerchant(
        merchantName: String,
        startDate: Long,
        endDate: Long,
    ): Flow<List<PeriodTotal>>

    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT T.categoryId, T.amount, T.description, T.notes, T.accountId
            FROM transactions AS T
            WHERE T.isSplit = 0 AND (:transactionType IS NULL OR T.transactionType = :transactionType) AND T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE
              AND (:accountId IS NULL OR T.accountId = :accountId)
            UNION ALL
            SELECT S.categoryId, S.amount, P.description, S.notes, P.accountId
            FROM split_transactions AS S JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE (:transactionType IS NULL OR P.transactionType = :transactionType) AND P.date BETWEEN :startDate AND :endDate AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE
              AND (:accountId IS NULL OR P.accountId = :accountId)
        )
        SELECT 
            C.name as categoryName, 
            SUM(AI.amount) as totalAmount,
            C.iconKey as iconKey,
            C.colorKey as colorKey
        FROM AtomicExpenses AS AI
        JOIN categories AS C ON AI.categoryId = C.id
        WHERE AI.categoryId IS NOT NULL
          AND (:keyword IS NULL OR LOWER(AI.description) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(AI.notes) LIKE '%' || LOWER(:keyword) || '%')
          AND (:categoryId IS NULL OR C.id = :categoryId)
        GROUP BY C.name
        ORDER BY totalAmount DESC
    """,
    )
    fun getSpendingByCategoryForMonth(
        startDate: Long,
        endDate: Long,
        keyword: String?,
        accountId: Int?,
        categoryId: Int?,
        transactionType: TransactionType?,
    ): Flow<List<CategorySpending>>

    @Query(
        """
        SELECT
            strftime('%Y-%m', T1.date / 1000, 'unixepoch', 'localtime') as monthYear,
            SUM(CASE WHEN T1.transactionType = $SQL_INCOME AND T1.isSplit = 0 THEN T1.amount ELSE 0 END) + 
            (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE strftime('%Y-%m', p.date / 1000, 'unixepoch', 'localtime') = strftime('%Y-%m', T1.date / 1000, 'unixepoch', 'localtime') AND p.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE AND p.transactionType = $SQL_INCOME) as totalIncome,
            SUM(CASE WHEN T1.transactionType = $SQL_EXPENSE AND T1.isSplit = 0 THEN T1.amount ELSE 0 END) + 
            (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE strftime('%Y-%m', p.date / 1000, 'unixepoch', 'localtime') = strftime('%Y-%m', T1.date / 1000, 'unixepoch', 'localtime') AND p.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE AND p.transactionType = $SQL_EXPENSE) as totalExpenses
        FROM transactions AS T1
        WHERE T1.date >= :startDate AND T1.isExcluded = 0 AND $SQL_T1_STATUS_ACTIVE
        GROUP BY monthYear
        ORDER BY monthYear ASC
    """,
    )
    fun getMonthlyTrends(startDate: Long): Flow<List<MonthlyTrend>>

    @Query(
        """
        SELECT
            SUM(CASE WHEN T.transactionType = $SQL_INCOME AND T.isSplit = 0 THEN T.amount ELSE 0 END) + (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE p.date BETWEEN :startDate AND :endDate AND p.transactionType = $SQL_INCOME AND p.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE) as totalIncome,
            SUM(CASE WHEN T.transactionType = $SQL_EXPENSE AND T.isSplit = 0 THEN T.amount ELSE 0 END) + (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE p.date BETWEEN :startDate AND :endDate AND p.transactionType = $SQL_EXPENSE AND p.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE) as totalExpenses
        FROM transactions AS T
        WHERE T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE
    """,
    )
    suspend fun getFinancialSummaryForRange(
        startDate: Long,
        endDate: Long,
    ): FinancialSummary?

    @Query(
        """
        SELECT
            SUM(CASE WHEN T.transactionType = $SQL_INCOME AND T.isSplit = 0 THEN T.amount ELSE 0 END) + (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE p.date BETWEEN :startDate AND :endDate AND p.transactionType = $SQL_INCOME AND p.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE) as totalIncome,
            SUM(CASE WHEN T.transactionType = $SQL_EXPENSE AND T.isSplit = 0 THEN T.amount ELSE 0 END) + (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE p.date BETWEEN :startDate AND :endDate AND p.transactionType = $SQL_EXPENSE AND p.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE) as totalExpenses
        FROM transactions AS T
        WHERE T.date BETWEEN :startDate AND :endDate AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE
    """,
    )
    fun getFinancialSummaryForRangeFlow(
        startDate: Long,
        endDate: Long,
    ): Flow<FinancialSummary?>

    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT T.date, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = $SQL_EXPENSE AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE
            UNION ALL
            SELECT P.date, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = $SQL_EXPENSE AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE
        )
        SELECT
            strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') as date,
            SUM(amount) as totalAmount
        FROM AtomicExpenses
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime')
        ORDER BY date ASC
    """,
    )
    fun getDailySpendingForDateRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<DailyTotal>>

    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT P.date, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = $SQL_EXPENSE AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE
            UNION ALL
            SELECT T.date, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = $SQL_EXPENSE AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE
        )
        SELECT
            strftime('%Y-%W', date / 1000, 'unixepoch', 'localtime') as period,
            SUM(amount) as totalAmount
        FROM AtomicExpenses
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY period
        ORDER BY period ASC
    """,
    )
    fun getWeeklySpendingForDateRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<PeriodTotal>>

    @Query(
        """
        WITH AtomicExpenses AS (
            SELECT P.date, S.amount FROM split_transactions AS S
            JOIN transactions AS P ON S.parentTransactionId = P.id
            WHERE P.transactionType = $SQL_EXPENSE AND P.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE
            UNION ALL
            SELECT T.date, T.amount FROM transactions AS T
            WHERE T.isSplit = 0 AND T.transactionType = $SQL_EXPENSE AND T.isExcluded = 0 AND $SQL_T_STATUS_ACTIVE
        )
        SELECT
            strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') as period,
            SUM(amount) as totalAmount
        FROM AtomicExpenses
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY period
        ORDER BY period ASC
    """,
    )
    fun getMonthlySpendingForDateRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<PeriodTotal>>

    @Query(
        """
        SELECT
            strftime('%Y-%m-%d', T1.date / 1000, 'unixepoch', 'localtime') as date,
            SUM(CASE WHEN T1.transactionType = $SQL_INCOME AND T1.isSplit = 0 THEN T1.amount ELSE 0 END) + 
            (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE strftime('%Y-%m-%d', p.date / 1000, 'unixepoch', 'localtime') = strftime('%Y-%m-%d', T1.date / 1000, 'unixepoch', 'localtime') AND p.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE AND p.transactionType = $SQL_INCOME) as totalIncome,
            SUM(CASE WHEN T1.transactionType = $SQL_EXPENSE AND T1.isSplit = 0 THEN T1.amount ELSE 0 END) + 
            (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE strftime('%Y-%m-%d', p.date / 1000, 'unixepoch', 'localtime') = strftime('%Y-%m-%d', T1.date / 1000, 'unixepoch', 'localtime') AND p.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE AND p.transactionType = $SQL_EXPENSE) as totalExpenses
        FROM transactions AS T1
        WHERE T1.date BETWEEN :startDate AND :endDate AND T1.isExcluded = 0 AND $SQL_T1_STATUS_ACTIVE
        GROUP BY strftime('%Y-%m-%d', T1.date / 1000, 'unixepoch', 'localtime')
        ORDER BY date ASC
    """,
    )
    fun getDailyTrends(
        startDate: Long,
        endDate: Long,
    ): Flow<List<DailyTrend>>

    @Query(
        """
        SELECT
            strftime('%Y-%W', T1.date / 1000, 'unixepoch', 'localtime') as period,
            SUM(CASE WHEN T1.transactionType = $SQL_INCOME AND T1.isSplit = 0 THEN T1.amount ELSE 0 END) + 
            (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE strftime('%Y-%W', p.date / 1000, 'unixepoch', 'localtime') = strftime('%Y-%W', T1.date / 1000, 'unixepoch', 'localtime') AND p.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE AND p.transactionType = $SQL_INCOME) as totalIncome,
            SUM(CASE WHEN T1.transactionType = $SQL_EXPENSE AND T1.isSplit = 0 THEN T1.amount ELSE 0 END) + 
            (SELECT IFNULL(SUM(s.amount), 0) FROM split_transactions s JOIN transactions p ON s.parentTransactionId = p.id WHERE strftime('%Y-%W', p.date / 1000, 'unixepoch', 'localtime') = strftime('%Y-%W', T1.date / 1000, 'unixepoch', 'localtime') AND p.isExcluded = 0 AND $SQL_P_STATUS_ACTIVE AND p.transactionType = $SQL_EXPENSE) as totalExpenses
        FROM transactions AS T1
        WHERE T1.date BETWEEN :startDate AND :endDate AND T1.isExcluded = 0 AND $SQL_T1_STATUS_ACTIVE
        GROUP BY strftime('%Y-%W', T1.date / 1000, 'unixepoch', 'localtime')
        ORDER BY period ASC
    """,
    )
    fun getWeeklyTrends(
        startDate: Long,
        endDate: Long,
    ): Flow<List<WeeklyTrend>>
}

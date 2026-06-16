// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/GoalTransactionLinkDao.kt
// REASON: NEW FILE (Issue #104) - DAO for the goal-transaction junction table.
// Provides methods to link/unlink transactions from goals and to compute
// dynamic progress totals.
// =================================================================================
package io.pm.finlight.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.pm.finlight.Transaction
import io.pm.finlight.data.db.entity.GoalTransactionLink
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalTransactionLinkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: GoalTransactionLink)

    @Query("DELETE FROM goal_transaction_links WHERE goalId = :goalId AND transactionId = :transactionId")
    suspend fun deleteLink(
        goalId: Int,
        transactionId: Int
    )

    @Query("DELETE FROM goal_transaction_links WHERE goalId = :goalId")
    suspend fun deleteAllLinksForGoal(goalId: Int)

    @Query("SELECT transactionId FROM goal_transaction_links WHERE goalId = :goalId")
    fun getLinkedTransactionIds(goalId: Int): Flow<List<Int>>

    /**
     * Returns the sum of linked transaction amounts for a specific goal.
     * This is the dynamic replacement for the deprecated Goal.savedAmount field.
     */
    @Query(
        """
        SELECT COALESCE(SUM(t.amount), 0.0) 
        FROM goal_transaction_links gtl 
        INNER JOIN transactions t ON gtl.transactionId = t.id 
        WHERE gtl.goalId = :goalId
        """,
    )
    fun getLinkedTransactionsTotal(goalId: Int): Flow<Double>

    @Query(
        """
        SELECT t.* FROM transactions t 
        INNER JOIN goal_transaction_links gtl ON t.id = gtl.transactionId 
        WHERE gtl.goalId = :goalId 
        ORDER BY t.date DESC
        """,
    )
    fun getLinkedTransactions(goalId: Int): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) > 0 FROM goal_transaction_links WHERE transactionId = :transactionId AND goalId = :goalId")
    suspend fun isTransactionLinkedToGoal(
        transactionId: Int,
        goalId: Int
    ): Boolean

    @Query("SELECT COUNT(*) FROM goal_transaction_links WHERE goalId = :goalId")
    fun getLinkedTransactionCount(goalId: Int): Flow<Int>

    /**
     * Retrieves all links, used for backup/restore.
     */
    @Query("SELECT * FROM goal_transaction_links")
    suspend fun getAll(): List<GoalTransactionLink>

    /**
     * Inserts a list of links, used during data restore.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<GoalTransactionLink>)

    /**
     * Deletes all links, used during data restore.
     */
    @Query("DELETE FROM goal_transaction_links")
    suspend fun deleteAll()
}

// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/GoalDao.kt
// REASON: FEATURE (Backup Phase 2) - Added `getAll`, `insertAll`, and `deleteAll`
// functions. These are required by the DataExportService to back up and restore
// all user-created savings goals.
// =================================================================================
package io.pm.finlight

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A data class to hold a Goal and its associated account's name.
 */
data class GoalWithAccountName(
    val id: Int,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double,
    val targetDate: Long?,
    val accountId: Int,
    val accountName: String
)

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: Goal)

    /**
     * Inserts a list of goals, used during data restore.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<Goal>)

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    /**
     * Deletes all goals, used during data restore.
     */
    @Query("DELETE FROM goals")
    suspend fun deleteAll()

    @Query("SELECT * FROM goals WHERE id = :id")
    fun getGoalById(id: Int): Flow<Goal?>

    /**
     * Retrieves all goals for backup purposes.
     */
    @Query("SELECT * FROM goals")
    suspend fun getAll(): List<Goal>

    @Query("""
        SELECT
            g.id, g.name, g.targetAmount, g.savedAmount, g.targetDate, g.accountId, a.name as accountName
        FROM goals as g
        INNER JOIN accounts as a ON g.accountId = a.id
        ORDER BY g.targetDate ASC
    """)
    fun getAllGoalsWithAccountName(): Flow<List<GoalWithAccountName>>

    @Query("SELECT * FROM goals WHERE accountId = :accountId")
    fun getGoalsForAccount(accountId: Int): Flow<List<Goal>>

    // --- NEW: Reassigns goals from source accounts to a destination account ---
    @Query("UPDATE goals SET accountId = :destinationAccountId WHERE accountId IN (:sourceAccountIds)")
    suspend fun reassignGoals(sourceAccountIds: List<Int>, destinationAccountId: Int)
}
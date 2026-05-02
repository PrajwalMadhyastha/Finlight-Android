// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/RecurringTransactionDao.kt
// REASON: FIX - The `insert` function now returns a `Long` representing the
// row ID of the newly inserted rule. This is required by the
// RecurringPatternWorker to get the ID for the notification deep link and
// resolves the `Unresolved reference 'toInt'` compilation error.
// =================================================================================
package io.pm.finlight

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recurringTransaction: RecurringTransaction): Long // --- FIX: Added Long return type

    @Update
    suspend fun update(recurringTransaction: RecurringTransaction)

    @Delete
    suspend fun delete(recurringTransaction: RecurringTransaction)

    @Query("SELECT * FROM recurring_transactions ORDER BY startDate DESC")
    fun getAllRulesFlow(): Flow<List<RecurringTransaction>>

    @Query("SELECT * FROM recurring_transactions")
    suspend fun getAllRulesList(): List<RecurringTransaction>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    fun getById(id: Int): Flow<RecurringTransaction?>

    @Query("UPDATE recurring_transactions SET lastRunDate = :lastRunDate WHERE id = :id")
    suspend fun updateLastRunDate(
        id: Int,
        lastRunDate: Long,
    )
}

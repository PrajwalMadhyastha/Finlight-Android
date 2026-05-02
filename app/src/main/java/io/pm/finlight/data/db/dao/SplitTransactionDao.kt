// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/SplitTransactionDao.kt
// REASON: FEATURE - Added `getAllSplits` and `getSplitsForParentSimple` queries.
// These are required by the data export service to fetch all split items for
// JSON and CSV backups.
// =================================================================================
package io.pm.finlight

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitTransactionDao {
    @Transaction
    @Query(
        """
        SELECT s.*, c.name as categoryName, c.iconKey as categoryIconKey, c.colorKey as categoryColorKey
        FROM split_transactions s
        LEFT JOIN categories c ON s.categoryId = c.id
        WHERE s.parentTransactionId = :parentTransactionId
        ORDER BY s.amount DESC
    """,
    )
    fun getSplitsForParent(parentTransactionId: Int): Flow<List<SplitTransactionDetails>>

    // --- NEW: Query to get simple split details for CSV export ---
    @Transaction
    @Query(
        """
        SELECT s.*, c.name as categoryName, c.iconKey as categoryIconKey, c.colorKey as categoryColorKey
        FROM split_transactions s
        LEFT JOIN categories c ON s.categoryId = c.id
        WHERE s.parentTransactionId = :parentTransactionId
        ORDER BY s.id ASC
    """,
    )
    suspend fun getSplitsForParentSimple(parentTransactionId: Int): List<SplitTransactionDetails>

    // --- NEW: Query to get all splits for JSON backup ---
    @Query("SELECT * FROM split_transactions")
    fun getAllSplits(): Flow<List<SplitTransaction>>

    // --- NEW: Query to clear the table during import ---
    @Query("DELETE FROM split_transactions")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(splits: List<SplitTransaction>)

    @Update
    suspend fun update(split: SplitTransaction)

    @Query("DELETE FROM split_transactions WHERE id = :splitId")
    suspend fun deleteById(splitId: Int)

    @Query("DELETE FROM split_transactions WHERE parentTransactionId = :parentTransactionId")
    suspend fun deleteSplitsForParent(parentTransactionId: Int)
}

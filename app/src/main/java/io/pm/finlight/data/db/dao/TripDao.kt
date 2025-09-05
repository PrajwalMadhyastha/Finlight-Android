// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/TripDao.kt
// REASON: FEATURE - Added a `deleteTripById` function. This is a critical
// part of the new "Cancel Trip" destructive action, allowing the ViewModel to
// remove the trip's record from the historical log.
// =================================================================================
package io.pm.finlight.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.pm.finlight.Trip
import kotlinx.coroutines.flow.Flow

/**
 * A Data Transfer Object to hold a trip's details along with its calculated stats.
 */
data class TripWithStats(
    val tripId: Int,
    val tripName: String,
    val startDate: Long,
    val endDate: Long,
    val totalSpend: Double,
    val transactionCount: Int,
    val tagId: Int
)

@Dao
interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: Trip): Long

    @Query("SELECT * FROM trips WHERE tagId = :tagId LIMIT 1")
    suspend fun getTripByTagId(tagId: Int): Trip?

    @Transaction
    @Query("""
        SELECT
            t.id as tripId,
            t.name as tripName,
            t.startDate,
            t.endDate,
            t.tagId,
            -- Sum of non-split transactions + sum of all splits for split transactions
            (
                -- Non-split transactions
                SELECT IFNULL(SUM(tx.amount), 0.0)
                FROM transactions tx
                JOIN transaction_tag_cross_ref ttcr ON tx.id = ttcr.transactionId
                WHERE ttcr.tagId = t.tagId AND tx.isSplit = 0 AND tx.transactionType = 'expense' AND tx.isExcluded = 0
            ) + (
                -- Split transactions
                SELECT IFNULL(SUM(s.amount), 0.0)
                FROM split_transactions s
                JOIN transactions p ON s.parentTransactionId = p.id
                JOIN transaction_tag_cross_ref ttcr ON p.id = ttcr.transactionId
                WHERE ttcr.tagId = t.tagId AND p.transactionType = 'expense' AND p.isExcluded = 0
            ) AS totalSpend,
            COUNT(DISTINCT tx_main.id) as transactionCount
        FROM trips t
        LEFT JOIN transaction_tag_cross_ref ttcr_main ON t.tagId = ttcr_main.tagId
        LEFT JOIN transactions tx_main ON ttcr_main.transactionId = tx_main.id
        GROUP BY t.id
        ORDER BY t.startDate DESC
    """)
    fun getAllTripsWithStats(): Flow<List<TripWithStats>>

    @Transaction
    @Query("""
        SELECT
            t.id as tripId,
            t.name as tripName,
            t.startDate,
            t.endDate,
            t.tagId,
            (
                SELECT IFNULL(SUM(tx.amount), 0.0)
                FROM transactions tx
                JOIN transaction_tag_cross_ref ttcr ON tx.id = ttcr.transactionId
                WHERE ttcr.tagId = t.tagId AND tx.isSplit = 0 AND tx.transactionType = 'expense' AND tx.isExcluded = 0
            ) + (
                SELECT IFNULL(SUM(s.amount), 0.0)
                FROM split_transactions s
                JOIN transactions p ON s.parentTransactionId = p.id
                JOIN transaction_tag_cross_ref ttcr ON p.id = ttcr.transactionId
                WHERE ttcr.tagId = t.tagId AND p.transactionType = 'expense' AND p.isExcluded = 0
            ) AS totalSpend,
            COUNT(DISTINCT tx_main.id) as transactionCount
        FROM trips t
        LEFT JOIN transaction_tag_cross_ref ttcr_main ON t.tagId = ttcr_main.tagId
        LEFT JOIN transactions tx_main ON ttcr_main.transactionId = tx_main.id
        WHERE t.id = :tripId
        GROUP BY t.id
    """)
    fun getTripWithStatsById(tripId: Int): Flow<TripWithStats?>

    // --- NEW: Delete a trip by its ID ---
    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTripById(tripId: Int)
}


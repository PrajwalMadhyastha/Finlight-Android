package io.pm.finlight.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.pm.finlight.data.db.entity.MergeRecord
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the [MergeRecord] table.
 *
 * Provides the minimum surface needed to record, observe, and delete merge
 * snapshots, plus backup/restore parity methods following the GoalTransactionLinkDao pattern.
 */
@Dao
interface MergeRecordDao {
    /**
     * Persists a new merge snapshot. Uses REPLACE strategy so that if a parent
     * is somehow merged again before unmerging, the latest record wins.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MergeRecord)

    /**
     * Observes the most recent merge record for a given parent transaction.
     * Emits null when no record exists (i.e. the transaction was never merged
     * or has already been unmerged). Used by the UI to decide whether to show
     * the "Unmerge" option.
     */
    @Query("SELECT * FROM merge_records WHERE parentTxnId = :parentTxnId ORDER BY mergedAt DESC LIMIT 1")
    fun observeForParent(parentTxnId: Int): Flow<MergeRecord?>

    /**
     * Synchronous lookup for the repository's unmerge logic.
     */
    @Query("SELECT * FROM merge_records WHERE parentTxnId = :parentTxnId ORDER BY mergedAt DESC LIMIT 1")
    suspend fun getForParentSync(parentTxnId: Int): MergeRecord?

    /**
     * Deletes a specific merge record by its own primary key, called after a
     * successful unmerge to clean up the snapshot.
     */
    @Query("DELETE FROM merge_records WHERE id = :id")
    suspend fun deleteById(id: Int)

    // ─── Backup / restore parity ────────────────────────────────────────────

    @Query("SELECT * FROM merge_records")
    suspend fun getAll(): List<MergeRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<MergeRecord>)

    @Query("DELETE FROM merge_records")
    suspend fun deleteAll()
}

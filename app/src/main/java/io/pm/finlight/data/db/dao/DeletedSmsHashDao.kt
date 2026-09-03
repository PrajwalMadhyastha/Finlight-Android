package io.pm.finlight.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.pm.finlight.data.db.entity.DeletedSmsHash

@Dao
interface DeletedSmsHashDao {
    /**
     * Record a hash as permanently deleted. Silently ignores duplicates
     * (idempotent — safe to call multiple times for the same hash).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(hash: DeletedSmsHash)

    /** Returns the full set of deleted hashes for use as a deny-list. */
    @Query("SELECT smsHash FROM deleted_sms_hashes")
    suspend fun getAllHashes(): List<String>

    /**
     * Removes a specific hash from the deny-list.
     * Called during unmerge so the child's source SMS is no longer treated as
     * "deleted" and will not be blocked from future re-processing.
     */
    @Query("DELETE FROM deleted_sms_hashes WHERE smsHash = :hash")
    suspend fun deleteByHash(hash: String)

    // --- Backup / Restore Parity ---

    @Query("SELECT * FROM deleted_sms_hashes")
    suspend fun getAll(): List<DeletedSmsHash>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(hashes: List<DeletedSmsHash>)

    @Query("DELETE FROM deleted_sms_hashes")
    suspend fun deleteAll()
}

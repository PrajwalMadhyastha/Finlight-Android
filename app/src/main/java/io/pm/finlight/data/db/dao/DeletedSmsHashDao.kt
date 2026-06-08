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
}

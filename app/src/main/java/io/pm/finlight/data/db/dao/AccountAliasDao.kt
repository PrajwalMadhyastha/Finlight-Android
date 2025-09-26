// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/AccountAliasDao.kt
// REASON: FEATURE (Backup Phase 2) - Added `getAll` and `deleteAll` functions.
// These are required by the DataExportService to back up and restore the
// learned account merge mappings.
// =================================================================================
package io.pm.finlight.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.pm.finlight.data.db.entity.AccountAlias

@Dao
interface AccountAliasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(aliases: List<AccountAlias>)

    @Query("SELECT * FROM account_aliases WHERE aliasName = :aliasName COLLATE NOCASE")
    suspend fun findByAlias(aliasName: String): AccountAlias?

    /**
     * Retrieves all account aliases for backup purposes.
     */
    @Query("SELECT * FROM account_aliases")
    suspend fun getAll(): List<AccountAlias>

    /**
     * Deletes all account aliases, used during data restore.
     */
    @Query("DELETE FROM account_aliases")
    suspend fun deleteAll()
}
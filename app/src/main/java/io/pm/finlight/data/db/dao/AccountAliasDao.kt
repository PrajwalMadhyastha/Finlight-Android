// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/AccountAliasDao.kt
// REASON: NEW FILE - This DAO provides the necessary methods to interact with
// the new `account_aliases` table. It allows the AccountRepository to save new
// aliases and the SmsReceiver to look them up.
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
}

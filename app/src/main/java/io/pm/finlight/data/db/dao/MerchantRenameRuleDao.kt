package io.pm.finlight

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantRenameRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: MerchantRenameRule)

    /**
     * Inserts a list of merchant rename rules.
     * @param rules The list of rules to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<MerchantRenameRule>)

    @Query("SELECT * FROM merchant_rename_rules")
    fun getAllRules(): Flow<List<MerchantRenameRule>>

    /**
     * Retrieves all merchant rename rules as a simple List for backup purposes.
     * @return A List of all MerchantRenameRule objects.
     */
    @Query("SELECT * FROM merchant_rename_rules")
    suspend fun getAllRulesList(): List<MerchantRenameRule>

    @Query("DELETE FROM merchant_rename_rules WHERE originalName = :originalName")
    suspend fun deleteByOriginalName(originalName: String)

    /**
     * Deletes all merchant rename rules from the database.
     */
    @Query("DELETE FROM merchant_rename_rules")
    suspend fun deleteAll()
}
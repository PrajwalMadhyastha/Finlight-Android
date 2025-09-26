package io.pm.finlight

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the CustomSmsRule entity.
 * Provides methods to interact with the custom_sms_rules table in the database.
 */
@Dao
interface CustomSmsRuleDao {

    /**
     * Inserts a new custom SMS rule into the database. If a rule with the same primary key
     * already exists, it will be replaced.
     *
     * @param rule The CustomSmsRule object to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CustomSmsRule)

    /**
     * Inserts a list of custom SMS rules.
     * @param rules The list of rules to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<CustomSmsRule>)

    /**
     * Retrieves all custom SMS rules from the database, ordered by priority in descending order.
     * This ensures that higher-priority rules are evaluated first.
     *
     * @return A Flow emitting a list of all CustomSmsRule objects.
     */
    @Query("SELECT * FROM custom_sms_rules ORDER BY priority DESC")
    fun getAllRules(): Flow<List<CustomSmsRule>>

    /**
     * Retrieves all custom SMS rules as a simple List for backup purposes.
     * @return A List of all CustomSmsRule objects.
     */
    @Query("SELECT * FROM custom_sms_rules")
    suspend fun getAllRulesList(): List<CustomSmsRule>

    /**
     * Deletes a specific custom rule from the database.
     *
     * @param rule The CustomSmsRule object to delete.
     */
    @Delete
    suspend fun delete(rule: CustomSmsRule)

    /**
     * Deletes all custom SMS rules from the database.
     */
    @Query("DELETE FROM custom_sms_rules")
    suspend fun deleteAll()

    @Query("SELECT * FROM custom_sms_rules WHERE id = :id")
    fun getRuleById(id: Int): Flow<CustomSmsRule?>

    @Update
    suspend fun update(rule: CustomSmsRule)
}
package io.pm.finlight

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the IgnoreRule entity.
 */
@Dao
interface IgnoreRuleDao {

    /**
     * Retrieves all ignore rules from the database, ordered alphabetically.
     * @return A Flow emitting a list of all IgnoreRule objects.
     */
    @Query("SELECT * FROM ignore_rules ORDER BY pattern ASC")
    fun getAll(): Flow<List<IgnoreRule>>

    /**
     * Retrieves all ignore rules as a simple List for backup purposes.
     * @return A List of all IgnoreRule objects.
     */
    @Query("SELECT * FROM ignore_rules")
    suspend fun getAllList(): List<IgnoreRule>

    /**
     * Retrieves all enabled ignore rules.
     * @return A list of the active IgnoreRule objects.
     */
    @Query("SELECT * FROM ignore_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<IgnoreRule>

    /**
     * Inserts a new ignore rule. If a rule with the same pattern already exists,
     * it will be ignored.
     * @param rule The IgnoreRule object to insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rule: IgnoreRule)

    /**
     * Inserts a list of ignore rules. Used for seeding the database.
     * @param rules The list of IgnoreRule objects to insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<IgnoreRule>)

    /**
     * Deletes all ignore rules from the database.
     */
    @Query("DELETE FROM ignore_rules")
    suspend fun deleteAll()

    /**
     * Updates an existing ignore rule.
     * @param rule The IgnoreRule object to update.
     */
    @Update
    suspend fun update(rule: IgnoreRule)

    /**
     * Deletes a specific ignore rule from the database.
     * @param rule The IgnoreRule object to delete.
     */
    @Delete
    suspend fun delete(rule: IgnoreRule)
}
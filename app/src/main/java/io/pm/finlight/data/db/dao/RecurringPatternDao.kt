// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/RecurringPatternDao.kt
// REASON: NEW FILE - This DAO provides the database interface for the
// RecurringPattern entity. It includes methods for inserting, updating,
// retrieving, and deleting patterns, which will be used by the new
// RecurringPatternWorker to track and analyze potential recurring transactions.
// =================================================================================
package io.pm.finlight

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RecurringPatternDao {
    /**
     * Inserts a new pattern. If a pattern with the same signature already exists, it is replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pattern: RecurringPattern)

    /**
     * Updates an existing pattern.
     */
    @Update
    suspend fun update(pattern: RecurringPattern)

    /**
     * Retrieves a pattern by its unique SMS signature.
     */
    @Query("SELECT * FROM recurring_patterns WHERE smsSignature = :signature")
    suspend fun getPatternBySignature(signature: String): RecurringPattern?

    /**
     * Retrieves all patterns from the database for analysis.
     */
    @Query("SELECT * FROM recurring_patterns")
    suspend fun getAllPatterns(): List<RecurringPattern>

    /**
     * Deletes a pattern by its signature, typically after a recurring rule has been created from it.
     */
    @Query("DELETE FROM recurring_patterns WHERE smsSignature = :signature")
    suspend fun deleteBySignature(signature: String)

    /**
     * Deletes all patterns. Used during backup restore to clear existing data before importing.
     */
    @Query("DELETE FROM recurring_patterns")
    suspend fun deleteAll()
}

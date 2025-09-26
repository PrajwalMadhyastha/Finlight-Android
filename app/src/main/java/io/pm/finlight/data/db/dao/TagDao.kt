// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/dao/TagDao.kt
// REASON: FEATURE (Backup Phase 2) - Added `getAllTagsList`, `insertAll`, and
// `deleteAll` functions. These are required by the DataExportService to back up
// and restore all user-created tags.
// =================================================================================
package io.pm.finlight

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    /**
     * Inserts a list of tags, used during data restore.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<Tag>)

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<Tag>>

    /**
     * Retrieves all tags as a simple List for backup purposes.
     */
    @Query("SELECT * FROM tags")
    suspend fun getAllTagsList(): List<Tag>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Tag?

    // --- NEW: Function to find a single tag by its ID ---
    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: Int): Tag?

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    /**
     * Deletes all tags, used during data restore.
     */
    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}
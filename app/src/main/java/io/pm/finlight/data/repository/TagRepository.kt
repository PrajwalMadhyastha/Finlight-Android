// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/TagRepository.kt
// REASON: FIX - Added the `findTagById` function to expose the new DAO method
// to the ViewModel layer. This is needed for the refactored trip editing logic.
// =================================================================================
package io.pm.finlight

import kotlinx.coroutines.flow.Flow

/**
 * Repository that abstracts access to the tag data source.
 * Now includes update and delete logic.
 */
class TagRepository(
    private val tagDao: TagDao,
    private val transactionDao: TransactionDao
) {

    val allTags: Flow<List<Tag>> = tagDao.getAllTags()

    suspend fun insert(tag: Tag): Long {
        return tagDao.insert(tag)
    }

    suspend fun update(tag: Tag) {
        tagDao.update(tag)
    }

    suspend fun delete(tag: Tag) {
        tagDao.delete(tag)
    }

    suspend fun isTagInUse(tagId: Int): Boolean {
        return transactionDao.countTransactionsForTag(tagId) > 0
    }

    /**
     * Finds a tag by its name. If it doesn't exist, it creates a new one.
     * @param tagName The name of the tag to find or create.
     * @return The existing or newly created Tag object.
     */
    suspend fun findOrCreateTag(tagName: String): Tag {
        val existingTag = tagDao.findByName(tagName)
        if (existingTag != null) {
            return existingTag
        }
        val newTag = Tag(name = tagName)
        val newId = tagDao.insert(newTag)
        return newTag.copy(id = newId.toInt())
    }

    // --- NEW: Function to find a tag by its ID ---
    suspend fun findTagById(id: Int): Tag? {
        return tagDao.getTagById(id)
    }
}

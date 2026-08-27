// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/TagRepository.kt
// REASON: REFACTOR - The `findByName` function has been added to expose the
// underlying DAO method. This is required by the refactored TagViewModel to check
// for existing tags before creating a new one, ensuring all data access goes
// through the repository layer.
package io.pm.finlight

import io.pm.finlight.data.db.dao.TransactionQueryDao
import kotlinx.coroutines.flow.Flow

/**
 * Repository that abstracts access to the tag data source.
 * Now includes update and delete logic.
 */
class TagRepository(
    private val tagDao: TagDao,
    private val transactionQueryDao: TransactionQueryDao,
) : ITagRepository {
    override val allTags: Flow<List<Tag>> = tagDao.getAllTags()

    override suspend fun insert(tag: Tag): Long {
        return tagDao.insert(tag)
    }

    override suspend fun update(tag: Tag) {
        tagDao.update(tag)
    }

    override suspend fun delete(tag: Tag) {
        tagDao.delete(tag)
    }

    override suspend fun isTagInUse(tagId: Int): Boolean {
        return transactionQueryDao.countTransactionsForTag(tagId) > 0
    }

    /**
     * Finds a tag by its name. If it doesn't exist, it creates a new one.
     * @param tagName The name of the tag to find or create.
     * @return The existing or newly created Tag object.
     */
    override suspend fun findOrCreateTag(tagName: String): Tag {
        val existingTag = tagDao.findByName(tagName)
        if (existingTag != null) {
            return existingTag
        }
        val newTag = Tag(name = tagName)
        val newId = tagDao.insert(newTag)
        return newTag.copy(id = newId.toInt())
    }

    // --- NEW: Function to find a tag by its ID ---
    override suspend fun findTagById(id: Int): Tag? {
        return tagDao.getTagById(id)
    }

    // --- NEW: Expose findByName for ViewModel logic ---
    override suspend fun findByName(name: String): Tag? {
        return tagDao.findByName(name)
    }
}

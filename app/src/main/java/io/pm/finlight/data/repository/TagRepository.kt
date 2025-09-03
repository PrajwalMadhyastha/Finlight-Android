// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/TagRepository.kt
// REASON: FEATURE - Unified Travel Mode. Added the `findOrCreateTag` function.
// This is a critical utility for the auto-tagging feature, allowing the
// ViewModel to get a Tag object for a trip name, creating it in the database
// if it's the first time that trip name has been used.
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
}

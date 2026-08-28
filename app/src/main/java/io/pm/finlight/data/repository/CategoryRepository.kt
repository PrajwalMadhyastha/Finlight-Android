// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/CategoryRepository.kt
// REASON: Updated the insert function to return the new category's ID (Long) and
// added a function to get a category by its ID.
// =================================================================================
package io.pm.finlight

import kotlinx.coroutines.flow.Flow

/**
 * Repository that abstracts access to the category data source.
 */
class CategoryRepository(private val categoryDao: CategoryDao) : ICategoryRepository {
    /**
     * Retrieves all categories from the category table, ordered by name.
     */
    override val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    /**
     * Retrieves a single category by its unique ID.
     */
    override suspend fun getCategoryById(id: Int): Category? {
        return categoryDao.getCategoryById(id)
    }

    override suspend fun findByName(name: String): Category? {
        return categoryDao.findByName(name)
    }

    /**
     * Inserts a category in a non-blocking way.
     */
    override suspend fun insert(category: Category): Long {
        return categoryDao.insert(category)
    }

    /**
     * Inserts a list of categories in a non-blocking way.
     */
    override suspend fun insertAll(categories: List<Category>) {
        categoryDao.insertAll(categories)
    }

    /**
     * Updates a category in a non-blocking way.
     */
    override suspend fun update(category: Category) {
        categoryDao.update(category)
    }

    /**
     * Deletes a category in a non-blocking way.
     */
    override suspend fun delete(category: Category) {
        categoryDao.delete(category)
    }
}

package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface ICategoryRepository {
    val allCategories: Flow<List<Category>>

    suspend fun getCategoryById(id: Int): Category?

    suspend fun findByName(name: String): Category?

    suspend fun insert(category: Category): Long

    suspend fun insertAll(categories: List<Category>)

    suspend fun update(category: Category)

    suspend fun delete(category: Category)
}

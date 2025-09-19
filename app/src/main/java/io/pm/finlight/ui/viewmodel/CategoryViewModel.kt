// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/CategoryViewModel.kt
// REASON: UX REFINEMENT - The `addCategory` function now performs a
// case-insensitive check to see if a category with the same name already exists
// before insertion. If it does, it sends a feedback message to the UI via the
// `uiEvent` channel, preventing silent failures.
// FIX - The unused `getCategoryById` function has been removed to resolve the
// "UnusedSymbol" warning.
// FIX - The instantiation of TransactionRepository has been updated to include
// the required dependencies, resolving a build error.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.utils.CategoryIconHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CategoryViewModel(application: Application) : AndroidViewModel(application) {
    private val categoryRepository: CategoryRepository
    private val transactionRepository: TransactionRepository
    private val categoryDao: CategoryDao // Expose DAO for direct checks

    val allCategories: Flow<List<Category>>
    private val _uiEvent = Channel<String>()
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        val db = AppDatabase.getInstance(application)
        val settingsRepository = SettingsRepository(application)
        val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
        categoryDao = db.categoryDao() // Initialize DAO
        categoryRepository = CategoryRepository(categoryDao)
        transactionRepository = TransactionRepository(db.transactionDao(), settingsRepository, tagRepository)
        allCategories = categoryRepository.allCategories
    }

    // --- UPDATED: Add pre-check and user feedback for duplicates ---
    fun addCategory(name: String, iconKey: String, colorKey: String) =
        viewModelScope.launch {
            // Check if a category with this name already exists
            val existingCategory = categoryDao.findByName(name)
            if (existingCategory != null) {
                _uiEvent.send("A category named '$name' already exists.")
                return@launch
            }

            val usedColorKeys = allCategories.firstOrNull()?.map { it.colorKey } ?: emptyList()
            val finalIconKey = if (iconKey == "category") "letter_default" else iconKey
            val finalColorKey = if (colorKey == "gray_light") {
                CategoryIconHelper.getNextAvailableColor(usedColorKeys)
            } else {
                colorKey
            }

            categoryRepository.insert(Category(name = name, iconKey = finalIconKey, colorKey = finalColorKey))
            _uiEvent.send("Category '$name' created.")
        }

    fun updateCategory(category: Category) =
        viewModelScope.launch {
            categoryRepository.update(category)
        }

    fun deleteCategory(category: Category) =
        viewModelScope.launch {
            val transactionCount = transactionRepository.countTransactionsForCategory(category.id)
            if (transactionCount == 0) {
                categoryRepository.delete(category)
                _uiEvent.send("Category '${category.name}' deleted.")
            } else {
                _uiEvent.send("Cannot delete '${category.name}'. It's used by $transactionCount transaction(s).")
            }
        }
}
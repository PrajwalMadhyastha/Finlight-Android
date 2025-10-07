// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/CategoryViewModel.kt
// REASON: FEATURE (Error Handling) - Added try-catch blocks to all data
// modification functions (`addCategory`, `updateCategory`, `deleteCategory`).
// This ensures that any exceptions thrown by the repository layer are caught
// gracefully and reported to the user via the UI event channel, improving the
// app's robustness.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.utils.CategoryIconHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryDao: CategoryDao
) : ViewModel() {

    val allCategories: Flow<List<Category>>
    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        allCategories = categoryRepository.allCategories
    }

    fun addCategory(name: String, iconKey: String, colorKey: String) =
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _uiEvent.send("Error creating category: ${e.message}")
            }
        }

    fun updateCategory(category: Category) =
        viewModelScope.launch {
            try {
                categoryRepository.update(category)
                _uiEvent.send("Category '${category.name}' updated.")
            } catch (e: Exception) {
                _uiEvent.send("Error updating category: ${e.message}")
            }
        }

    fun deleteCategory(category: Category) =
        viewModelScope.launch {
            try {
                val transactionCount = transactionRepository.countTransactionsForCategory(category.id)
                if (transactionCount == 0) {
                    categoryRepository.delete(category)
                    _uiEvent.send("Category '${category.name}' deleted.")
                } else {
                    _uiEvent.send("Cannot delete '${category.name}'. It's used by $transactionCount transaction(s).")
                }
            } catch (e: Exception) {
                _uiEvent.send("Error deleting category: ${e.message}")
            }
        }
}
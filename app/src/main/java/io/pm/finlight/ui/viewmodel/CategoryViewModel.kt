// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/CategoryViewModel.kt
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
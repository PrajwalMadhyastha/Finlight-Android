package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.Category
import io.pm.finlight.ICategoryRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val categoryRepository: ICategoryRepository,
    private val transactionRepository: ITransactionRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    val allCategories: Flow<List<Category>>
    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        allCategories = categoryRepository.allCategories
    }

    fun addCategory(
        name: String,
        iconKey: String,
        colorKey: String,
    ) = viewModelScope.launch(dispatcherProvider.io) {
        try {
            // Check if a category with this name already exists
            val existingCategory = categoryRepository.findByName(name)
            if (existingCategory != null) {
                _uiEvent.send("A category named '$name' already exists.")
                return@launch
            }

            val usedColorKeys = categoryRepository.getAllCategoriesSnapshot().map { it.colorKey }
            val finalIconKey = if (iconKey == "category") "letter_default" else iconKey
            val finalColorKey =
                if (colorKey == "gray_light") {
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
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                categoryRepository.update(category)
                _uiEvent.send("Category '${category.name}' updated.")
            } catch (e: Exception) {
                _uiEvent.send("Error updating category: ${e.message}")
            }
        }

    fun deleteCategory(category: Category) =
        viewModelScope.launch(dispatcherProvider.io) {
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

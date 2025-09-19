// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/SplitTransactionViewModel.kt
// REASON: FEATURE (Travel Mode Splitting) - The ViewModel's initialization
// logic now checks if the parent transaction has a foreign currency amount. If
// so, it uses that as the total for splitting and populates the initial split
// item with the foreign amount, preparing the UI for foreign currency input.
// FIX - The instantiation of TransactionRepository has been updated to include
// the required dependencies, resolving a build error.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SplitItem(
    val id: Int, // Can be a temporary ID for new items, or the real ID for existing ones
    val amount: String,
    val category: Category?,
    val notes: String?
)

data class SplitTransactionUiState(
    val parentTransaction: Transaction? = null,
    val splitItems: List<SplitItem> = emptyList(),
    val remainingAmount: Double = 0.0,
    val isSaving: Boolean = false,
    val error: String? = null
)

class SplitTransactionViewModelFactory(
    private val application: Application,
    private val transactionId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SplitTransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SplitTransactionViewModel(application, transactionId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SplitTransactionViewModel(
    application: Application,
    private val transactionId: Int
) : ViewModel() {

    private val db = AppDatabase.getInstance(application)
    private val transactionRepository: TransactionRepository
    val categoryRepository = CategoryRepository(db.categoryDao())
    private val splitTransactionRepository = SplitTransactionRepository(db.splitTransactionDao())


    private val _uiState = MutableStateFlow(SplitTransactionUiState())
    val uiState = _uiState.asStateFlow()

    private var nextTempId = -1 // For unique keys for new items

    init {
        val settingsRepository = SettingsRepository(application)
        val tagRepository = TagRepository(db.tagDao(), db.transactionDao())
        transactionRepository = TransactionRepository(db.transactionDao(), settingsRepository, tagRepository)

        viewModelScope.launch {
            val parentTxn = transactionRepository.getTransactionById(transactionId).firstOrNull()
            if (parentTxn != null) {
                if (parentTxn.isSplit) {
                    val existingSplits = splitTransactionRepository.getSplitsForParent(transactionId).firstOrNull()
                    if (!existingSplits.isNullOrEmpty()) {
                        val splitItems = existingSplits.map { details ->
                            // --- UPDATED: Use originalAmount if available, otherwise fallback to home currency amount ---
                            val displayAmount = details.splitTransaction.originalAmount ?: details.splitTransaction.amount
                            SplitItem(
                                id = details.splitTransaction.id,
                                amount = displayAmount.toString(),
                                category = categoryRepository.getCategoryById(details.splitTransaction.categoryId ?: -1),
                                notes = details.splitTransaction.notes
                            )
                        }
                        _uiState.value = SplitTransactionUiState(
                            parentTransaction = parentTxn,
                            splitItems = splitItems,
                            remainingAmount = 0.0
                        )
                    } else {
                        initializeForCreation(parentTxn)
                    }
                } else {
                    initializeForCreation(parentTxn)
                }
            } else {
                _uiState.value = SplitTransactionUiState(error = "Transaction not found.")
            }
        }
    }

    private fun initializeForCreation(parentTxn: Transaction) {
        // --- UPDATED: Use originalAmount for splitting if it exists ---
        val amountToSplit = parentTxn.originalAmount ?: parentTxn.amount
        val initialSplit = SplitItem(
            id = nextTempId--,
            amount = amountToSplit.toString(),
            category = null,
            notes = parentTxn.notes
        )
        _uiState.value = SplitTransactionUiState(
            parentTransaction = parentTxn,
            splitItems = listOf(initialSplit),
            remainingAmount = 0.0
        )
    }

    fun addSplitItem() {
        _uiState.update { currentState ->
            val newSplit = SplitItem(id = nextTempId--, amount = "0.0", category = null, notes = null)
            currentState.copy(splitItems = currentState.splitItems + newSplit)
        }
        recalculateRemainingAmount()
    }

    fun removeSplitItem(itemToRemove: SplitItem) {
        _uiState.update { currentState ->
            currentState.copy(splitItems = currentState.splitItems.filter { it.id != itemToRemove.id })
        }
        recalculateRemainingAmount()
    }

    fun updateSplitAmount(itemToUpdate: SplitItem, newAmount: String) {
        _uiState.update { currentState ->
            val updatedList = currentState.splitItems.map {
                if (it.id == itemToUpdate.id) it.copy(amount = newAmount) else it
            }
            currentState.copy(splitItems = updatedList)
        }
        recalculateRemainingAmount()
    }

    fun updateSplitCategory(itemToUpdate: SplitItem, newCategory: Category?) {
        _uiState.update { currentState ->
            val updatedList = currentState.splitItems.map {
                if (it.id == itemToUpdate.id) it.copy(category = newCategory) else it
            }
            currentState.copy(splitItems = updatedList)
        }
    }

    private fun recalculateRemainingAmount() {
        _uiState.update { currentState ->
            // --- UPDATED: Use originalAmount for calculation if it exists ---
            val parentAmount = currentState.parentTransaction?.originalAmount ?: currentState.parentTransaction?.amount ?: 0.0
            val totalSplitAmount = currentState.splitItems.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
            currentState.copy(remainingAmount = parentAmount - totalSplitAmount)
        }
    }
}
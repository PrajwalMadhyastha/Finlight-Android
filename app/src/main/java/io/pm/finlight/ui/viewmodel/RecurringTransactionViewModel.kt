// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/RecurringTransactionViewModel.kt
// REASON: REFACTOR (Testing) - The ViewModel now uses constructor dependency
// injection for the Application context and its Repository. This decouples it
// from AndroidViewModel, making it fully unit-testable.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.utils.ReminderManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class RecurringTransactionViewModel(
    private val application: Application,
    private val repository: RecurringTransactionRepository
) : ViewModel() {

    val allRecurringTransactions: Flow<List<RecurringTransaction>> = repository.getAll()

    fun getRuleById(id: Int): Flow<RecurringTransaction?> = repository.getById(id)

    fun saveRule(
        ruleId: Int?, // Null for new rules
        description: String,
        amount: Double,
        transactionType: String,
        recurrenceInterval: String,
        startDate: Long,
        accountId: Int,
        categoryId: Int?,
        lastRunDate: Long? // Preserve last run date on edit
    ) = viewModelScope.launch {
        val rule = RecurringTransaction(
            id = ruleId ?: 0,
            description = description,
            amount = amount,
            transactionType = transactionType,
            recurrenceInterval = recurrenceInterval,
            startDate = startDate,
            accountId = accountId,
            categoryId = categoryId,
            lastRunDate = lastRunDate
        )

        if (ruleId != null) {
            repository.update(rule)
        } else {
            repository.insert(rule)
            // Only schedule the worker when a new rule is added for the first time
            ReminderManager.scheduleRecurringTransactionWorker(application)
        }
    }

    fun deleteRule(rule: RecurringTransaction) = viewModelScope.launch {
        repository.delete(rule)
    }
}
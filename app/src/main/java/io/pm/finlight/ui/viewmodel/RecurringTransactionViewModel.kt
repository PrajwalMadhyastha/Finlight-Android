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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecurringTransactionViewModel(
    private val application: Application,
    private val repository: RecurringTransactionRepository,
    private val patternDao: RecurringPatternDao,
) : ViewModel() {
    val allRecurringTransactions: Flow<List<RecurringTransaction>> = repository.getAll()

    val patternSuggestions: StateFlow<List<RecurringPattern>> =
        patternDao.getUnacknowledgedPatterns()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun getRuleById(id: Int): Flow<RecurringTransaction?> = repository.getById(id)

    fun dismissPatternSuggestion(pattern: RecurringPattern) =
        viewModelScope.launch {
            patternDao.update(pattern.copy(isDismissed = true))
        }

    fun saveRule(
        // Null for new rules
        ruleId: Int?,
        description: String,
        amount: Double,
        transactionType: String,
        recurrenceInterval: String,
        startDate: Long,
        accountId: Int,
        categoryId: Int?,
        // Preserve last run date on edit
        lastRunDate: Long?,
        // New Issue #105 fields
        isVariableBill: Boolean = false,
        autoApprove: Boolean = false,
        endDate: Long? = null,
        smsSenderId: String? = null,
        skipCount: Int = 0,
    ) = viewModelScope.launch {
        val rule =
            RecurringTransaction(
                id = ruleId ?: 0,
                description = description,
                amount = amount,
                transactionType = transactionType,
                recurrenceInterval = recurrenceInterval,
                startDate = startDate,
                accountId = accountId,
                categoryId = categoryId,
                lastRunDate = lastRunDate,
                isVariableBill = isVariableBill,
                autoApprove = autoApprove,
                endDate = endDate,
                smsSenderId = smsSenderId,
                skipCount = skipCount,
            )

        if (ruleId != null) {
            repository.update(rule)
        } else {
            repository.insert(rule)
            // Only schedule the worker when a new rule is added for the first time
            ReminderManager.scheduleRecurringTransactionWorker(application)
        }
    }

    fun deleteRule(rule: RecurringTransaction) =
        viewModelScope.launch {
            repository.delete(rule)
        }
}

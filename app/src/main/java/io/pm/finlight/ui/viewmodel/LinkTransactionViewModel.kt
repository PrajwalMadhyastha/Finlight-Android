// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/LinkTransactionViewModel.kt
// REASON: REFACTOR (Testing) - The ViewModel has been refactored to use
// constructor dependency injection for its repository dependencies. It now extends
// ViewModel instead of AndroidViewModel, decoupling it from the Android framework
// and making it fully unit-testable.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.RecurringTransactionDao
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class LinkTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val recurringTransactionDao: RecurringTransactionDao,
    val potentialTransaction: PotentialTransaction
) : ViewModel() {

    private val _linkableTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val linkableTransactions = _linkableTransactions.asStateFlow()

    init {
        findMatches()
    }

    private fun findMatches() {
        viewModelScope.launch {
            val todayEnd = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }.timeInMillis

            val yesterdayStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis

            // Use the existing DAO method to get all transactions in the date range.
            // This returns a Flow, so we collect it to update our state.
            transactionRepository.getAllTransactionsForRange(yesterdayStart, todayEnd)
                .collect { transactions ->
                    _linkableTransactions.value = transactions
                }
        }
    }

    fun linkTransaction(selectedTransactionId: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            // Link the transaction by setting its hash
            potentialTransaction.sourceSmsHash?.let { hash ->
                transactionRepository.setSmsHash(selectedTransactionId, hash)
            }
            // Update the last run date of the rule to today
            val ruleId = potentialTransaction.sourceSmsId.toInt()
            recurringTransactionDao.updateLastRunDate(ruleId, System.currentTimeMillis())
            onComplete()
        }
    }

    fun remindTomorrow(onComplete: () -> Unit) {
        viewModelScope.launch {
            // To "remind tomorrow", we simply update the last run date to yesterday.
            // This makes the rule due again starting today.
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            val ruleId = potentialTransaction.sourceSmsId.toInt()
            recurringTransactionDao.updateLastRunDate(ruleId, yesterday)
            onComplete()
        }
    }
}
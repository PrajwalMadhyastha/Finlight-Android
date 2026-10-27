package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class LinkRecurringTransactionViewModel(
    private val transactionRepository: ITransactionRepository,
    private val recurringTransactionRepository: IRecurringTransactionRepository,
    val potentialTransaction: PotentialTransaction,
) : ViewModel() {
    private val _linkableTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val linkableTransactions = _linkableTransactions.asStateFlow()

    init {
        findMatches()
    }

    private fun findMatches() {
        viewModelScope.launch {
            val todayEnd =
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }.timeInMillis

            val yesterdayStart =
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.timeInMillis

            transactionRepository.getAllTransactionsForRange(yesterdayStart, todayEnd)
                .collect { transactions ->
                    _linkableTransactions.value = transactions
                }
        }
    }

    fun linkTransaction(
        selectedTransactionId: Int,
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch {
            // Link the transaction by setting its hash if available
            potentialTransaction.sourceSmsHash?.let { hash ->
                transactionRepository.setSmsHash(selectedTransactionId, hash)
            }
            // Update the last run date of the recurring rule to today
            val ruleId = potentialTransaction.sourceSmsId.toInt()
            recurringTransactionRepository.updateLastRunDate(ruleId, System.currentTimeMillis())
            onComplete()
        }
    }

    fun remindTomorrow(onComplete: () -> Unit) {
        viewModelScope.launch {
            // To "remind tomorrow", update the last run date to yesterday so it triggers next cycle
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            val ruleId = potentialTransaction.sourceSmsId.toInt()
            recurringTransactionRepository.updateLastRunDate(ruleId, yesterday)
            onComplete()
        }
    }
}

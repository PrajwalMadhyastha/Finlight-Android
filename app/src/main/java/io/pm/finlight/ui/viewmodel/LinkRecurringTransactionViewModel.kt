package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.viewModelScope
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import kotlinx.coroutines.launch
import java.util.Calendar

class LinkRecurringTransactionViewModel(
    transactionRepository: ITransactionRepository,
    private val recurringTransactionRepository: IRecurringTransactionRepository,
    potentialTransaction: PotentialTransaction,
) : LinkTransactionViewModel(transactionRepository, potentialTransaction) {
    override fun linkTransaction(
        selectedTransactionId: Int,
        onComplete: () -> Unit,
    ) {
        super.linkTransaction(selectedTransactionId) {
            val ruleId = potentialTransaction.sourceSmsId.toInt()
            viewModelScope.launch {
                recurringTransactionRepository.updateLastRunDate(ruleId, System.currentTimeMillis())
                onComplete()
            }
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

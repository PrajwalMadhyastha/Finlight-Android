package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.viewModelScope
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class LinkRecurringTransactionViewModel(
    transactionRepository: ITransactionRepository,
    private val recurringTransactionRepository: IRecurringTransactionRepository,
    potentialTransaction: PotentialTransaction,
    dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : LinkTransactionViewModel(transactionRepository, potentialTransaction, dispatcherProvider) {
    override fun linkTransaction(
        selectedTransactionId: Int,
        onComplete: (Boolean) -> Unit,
    ) {
        super.linkTransaction(selectedTransactionId) { success ->
            if (!success) {
                onComplete(false)
                return@linkTransaction
            }
            val ruleId = potentialTransaction.sourceSmsId.toInt()
            viewModelScope.launch(dispatcherProvider.io) {
                try {
                    recurringTransactionRepository.updateLastRunDate(ruleId, System.currentTimeMillis())
                    withContext(dispatcherProvider.main) {
                        onComplete(true)
                    }
                } catch (e: Exception) {
                    _uiEvent.send("Error updating recurring rule: ${e.message}")
                    withContext(dispatcherProvider.main) {
                        onComplete(false)
                    }
                }
            }
        }
    }

    fun remindTomorrow(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                // To "remind tomorrow", update the last run date to yesterday so it triggers next cycle
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
                val ruleId = potentialTransaction.sourceSmsId.toInt()
                recurringTransactionRepository.updateLastRunDate(ruleId, yesterday)
                withContext(dispatcherProvider.main) {
                    onComplete(true)
                }
            } catch (e: Exception) {
                _uiEvent.send("Error reminding tomorrow: ${e.message}")
                withContext(dispatcherProvider.main) {
                    onComplete(false)
                }
            }
        }
    }
}

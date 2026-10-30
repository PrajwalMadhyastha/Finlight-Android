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
        onComplete: () -> Unit,
    ) {
        super.linkTransaction(selectedTransactionId) {
            val ruleId = potentialTransaction.sourceSmsId.toInt()
            viewModelScope.launch(dispatcherProvider.io) {
                try {
                    recurringTransactionRepository.updateLastRunDate(ruleId, System.currentTimeMillis())
                } catch (_: Exception) {
                    // Ignore or handle
                } finally {
                    withContext(dispatcherProvider.main) {
                        onComplete()
                    }
                }
            }
        }
    }

    fun remindTomorrow(onComplete: () -> Unit) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                // To "remind tomorrow", update the last run date to yesterday so it triggers next cycle
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
                val ruleId = potentialTransaction.sourceSmsId.toInt()
                recurringTransactionRepository.updateLastRunDate(ruleId, yesterday)
            } catch (_: Exception) {
                // Ignore or handle
            } finally {
                withContext(dispatcherProvider.main) {
                    onComplete()
                }
            }
        }
    }
}

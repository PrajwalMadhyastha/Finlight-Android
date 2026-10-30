package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.Transaction
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

open class LinkTransactionViewModel(
    protected val transactionRepository: ITransactionRepository,
    val potentialTransaction: PotentialTransaction,
    protected val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    private val _linkableTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val linkableTransactions = _linkableTransactions.asStateFlow()

    init {
        findMatches()
    }

    private fun findMatches() {
        viewModelScope.launch(dispatcherProvider.io) {
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

            // Use the existing DAO method to get all transactions in the date range.
            // This returns a Flow, so we collect it to update our state.
            transactionRepository.getAllTransactionsForRange(yesterdayStart, todayEnd)
                .collect { transactions ->
                    _linkableTransactions.value = transactions
                }
        }
    }

    open fun linkTransaction(
        selectedTransactionId: Int,
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                // Link the transaction by setting its hash
                potentialTransaction.sourceSmsHash?.let { hash ->
                    transactionRepository.setSmsHash(selectedTransactionId, hash)
                }
                withContext(dispatcherProvider.main) { onComplete() }
            } catch (_: Exception) {
                withContext(dispatcherProvider.main) { onComplete() }
            }
        }
    }
}

// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/PendingTransactionsViewModel.kt
// REASON: REFACTOR (Issue #338) - Decouples PendingTransactionsViewModel from
// AndroidViewModel and direct Room database/DAO access, injecting repository interfaces
// (ITransactionRepository, IRecurringTransactionRepository) and DispatcherProvider.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.Transaction
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PendingTransactionsViewModel(
    private val transactionRepository: ITransactionRepository,
    private val recurringTransactionRepository: IRecurringTransactionRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel() {
    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent: Flow<String> = _uiEvent.receiveAsFlow()

    /** All PENDING draft transactions, ordered oldest-first (most overdue first). */
    val pendingTransactions: StateFlow<List<Transaction>> =
        transactionRepository.getPendingTransactionsFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    /**
     * Confirms a pending draft transaction.
     *
     * @param draftId The ID of the PENDING Transaction to confirm.
     * @param ruleId The ID of the recurring rule that generated this draft, or null.
     * @param confirmedAmount If non-null, overrides the draft amount before confirming.
     *                       Used when the user edits the amount on a variable bill.
     * @param lastRunDate The timestamp of last execution (defaults to current system time).
     */
    fun confirmPending(
        draftId: Int,
        ruleId: Int? = null,
        confirmedAmount: Double? = null,
        lastRunDate: Long = System.currentTimeMillis(),
    ) {
        if (confirmedAmount != null && (confirmedAmount <= 0.0 || confirmedAmount.isNaN() || confirmedAmount.isInfinite())) {
            Log.e(TAG, "Cannot confirm draft $draftId: Invalid confirmedAmount $confirmedAmount")
            _uiEvent.trySend("Invalid amount: must be greater than 0")
            return
        }
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                withContext(NonCancellable) {
                    transactionRepository.confirmTransaction(draftId, confirmedAmount)
                    if (ruleId != null) {
                        // Update the rule's lastRunDate so it doesn't fire again this cycle
                        // and reset skip counter on successful confirmation
                        recurringTransactionRepository.updateLastRunAndSkipCount(
                            ruleId = ruleId,
                            lastRunDate = lastRunDate,
                            skipCount = 0,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to confirm pending transaction $draftId", e)
                _uiEvent.send("Failed to confirm transaction: ${e.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Skips the current cycle for a pending draft.
     *
     * @param draftId The ID of the PENDING Transaction to skip.
     * @param ruleId The ID of the recurring rule that generated this draft, or null.
     * @param lastRunDate The timestamp of last execution (defaults to current system time).
     * @param skipCount Optional target skip count. If null, the existing skip count is incremented by 1.
     */
    fun skipPending(
        draftId: Int,
        ruleId: Int? = null,
        lastRunDate: Long = System.currentTimeMillis(),
        skipCount: Int? = null,
    ) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                withContext(NonCancellable) {
                    transactionRepository.skipTransaction(draftId)
                    if (ruleId != null) {
                        val finalSkipCount =
                            if (skipCount != null) {
                                skipCount
                            } else {
                                val rule = recurringTransactionRepository.getRuleById(ruleId)
                                rule?.let { it.skipCount + 1 }
                            }
                        if (finalSkipCount != null) {
                            recurringTransactionRepository.updateLastRunAndSkipCount(
                                ruleId = ruleId,
                                lastRunDate = lastRunDate,
                                skipCount = finalSkipCount,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to skip pending transaction $draftId", e)
                _uiEvent.send("Failed to skip transaction: ${e.message ?: "Unknown error"}")
            }
        }
    }

    companion object {
        private const val TAG = "PendingTxViewModel"
    }
}

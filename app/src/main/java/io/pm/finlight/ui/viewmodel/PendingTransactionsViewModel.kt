// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/viewmodel/PendingTransactionsViewModel.kt
// REASON: NEW FILE (Issue #105) - Manages the state and actions for the
// ConfirmPendingBottomSheet and the UPCOMING_PAYMENTS dashboard card.
// Exposes:
//  - pendingTransactions: Flow<List<Transaction>> — the user's action queue.
//  - confirmPending(draftId, ruleId, confirmedAmount) — confirm with optional amount edit.
//  - skipPending(draftId, ruleId) — skip this cycle, increment rule skipCount.
// =================================================================================
package io.pm.finlight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PendingTransactionsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val transactionDao = db.transactionDao()
    private val recurringDao = db.recurringTransactionDao()

    /** All PENDING draft transactions, ordered oldest-first (most overdue first). */
    val pendingTransactions: StateFlow<List<Transaction>> =
        transactionDao.getPendingTransactions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    /**
     * Confirms a pending draft transaction.
     *
     * @param draftId The ID of the PENDING Transaction to confirm.
     * @param ruleId  The ID of the recurring rule that generated this draft.
     * @param confirmedAmount If non-null, overrides the draft amount before confirming.
     *                       Used when the user edits the amount on a variable bill.
     */
    fun confirmPending(
        draftId: Int,
        ruleId: Int,
        confirmedAmount: Double? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (confirmedAmount != null) {
                // Update amount before confirming
                transactionDao.updateAmount(draftId, confirmedAmount)
            }
            transactionDao.confirmTransaction(draftId)
            // Update the rule's lastRunDate so it doesn't fire again this cycle
            recurringDao.updateLastRunDate(ruleId, System.currentTimeMillis())
            // Reset skip counter on successful confirmation
            recurringDao.updateSkipCount(ruleId, 0)
        }
    }

    /**
     * Skips the current cycle for a pending draft.
     *
     * @param draftId The ID of the PENDING Transaction to skip.
     * @param ruleId  The ID of the recurring rule that generated this draft.
     */
    fun skipPending(
        draftId: Int,
        ruleId: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.skipTransaction(draftId)
            // Update lastRunDate so the worker doesn't recreate a draft for this cycle
            recurringDao.updateLastRunDate(ruleId, System.currentTimeMillis())
            // Increment the skip counter (groundwork for future cancellation detection)
            val rule = recurringDao.getAllRulesList().find { it.id == ruleId }
            if (rule != null) {
                recurringDao.updateSkipCount(ruleId, rule.skipCount + 1)
            }
        }
    }
}

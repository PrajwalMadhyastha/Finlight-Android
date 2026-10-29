package io.pm.finlight.ui.viewmodel.delegate

import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionStatus
import io.pm.finlight.data.db.entity.MergeRecord
import io.pm.finlight.data.model.MergedTransactionItem
import io.pm.finlight.domain.usecase.MergeTransactionsUseCase
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionMergeDelegate(
    private val coroutineScope: CoroutineScope,
    private val mergeTransactionsUseCase: MergeTransactionsUseCase,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) {
    private val _showReviewMergeSheet = MutableStateFlow(false)
    val showReviewMergeSheet: StateFlow<Boolean> = _showReviewMergeSheet.asStateFlow()

    private val _anchorTransactionId = MutableStateFlow<Int?>(null)
    val anchorTransactionId: StateFlow<Int?> = _anchorTransactionId.asStateFlow()

    private val _mergedTransactionBreakdown = MutableStateFlow<List<MergedTransactionItem>>(emptyList())
    val mergedTransactionBreakdown: StateFlow<List<MergedTransactionItem>> = _mergedTransactionBreakdown.asStateFlow()

    private val _mergeValidationError = MutableStateFlow<String?>(null)
    val mergeValidationError: StateFlow<String?> = _mergeValidationError.asStateFlow()

    fun canManualMerge(
        selectedTransactionIds: Flow<Set<Int>>,
        transactionsForSelectedMonth: Flow<List<TransactionDetails>>,
    ): StateFlow<Boolean> =
        combine(selectedTransactionIds, transactionsForSelectedMonth) { ids, txns ->
            if (ids.size < 2) return@combine false
            val selected = txns.filter { it.transaction.id in ids }
            val hasSplit = selected.any { it.transaction.isSplit }
            val hasPending =
                selected.any {
                    it.transaction.status == TransactionStatus.PENDING || it.transaction.status == TransactionStatus.SKIPPED
                }
            !hasSplit && !hasPending
        }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), false)

    fun openReviewMergeSheet(
        selectedIds: Set<Int>,
        allTxns: List<TransactionDetails>,
    ) {
        if (selectedIds.size < 2) return
        coroutineScope.launch {
            val selected = allTxns.filter { it.transaction.id in selectedIds }
            val largestTxn = selected.maxByOrNull { it.transaction.amount }
            if (largestTxn != null) {
                _anchorTransactionId.value = largestTxn.transaction.id
            }
            _showReviewMergeSheet.value = true
        }
    }

    fun dismissReviewMergeSheet() {
        _showReviewMergeSheet.value = false
        _anchorTransactionId.value = null
    }

    fun setAnchorTransaction(
        transactionId: Int,
        selectedIds: Set<Int>,
    ) {
        if (selectedIds.contains(transactionId)) {
            _anchorTransactionId.value = transactionId
        }
    }

    fun confirmManualMerge(
        selectedIds: Set<Int>,
        onEvent: suspend (String) -> Unit,
        onClearSelection: () -> Unit,
    ) {
        coroutineScope.launch {
            val anchorId = _anchorTransactionId.value
            if (anchorId == null || selectedIds.size < 2 || !selectedIds.contains(anchorId)) {
                onEvent("Invalid merge configuration.")
                return@launch
            }

            val childIds = selectedIds.filter { it != anchorId }

            try {
                mergeTransactionsUseCase.manualMerge(anchorId, childIds)
                onEvent("Successfully merged ${selectedIds.size} transactions.")
                dismissReviewMergeSheet()
                onClearSelection()
            } catch (e: Exception) {
                onEvent("Failed to merge transactions.")
            }
        }
    }

    fun loadMergedTransactionBreakdown(transactionId: Int) {
        coroutineScope.launch(dispatcherProvider.io) {
            _mergedTransactionBreakdown.value =
                mergeTransactionsUseCase.getMergedTransactionBreakdown(transactionId)
        }
    }

    fun observeMergeRecord(parentTxnId: Int): Flow<MergeRecord?> =
        mergeTransactionsUseCase.observeMergeRecord(parentTxnId)

    fun unmergeTransaction(
        parentTxnId: Int,
        onEvent: suspend (String) -> Unit = {},
    ) {
        coroutineScope.launch(dispatcherProvider.io) {
            try {
                mergeTransactionsUseCase.unmerge(parentTxnId)
            } catch (e: Exception) {
                onEvent("Failed to unmerge transactions.")
            }
        }
    }

    fun resetBreakdown() {
        _mergedTransactionBreakdown.value = emptyList()
    }
}

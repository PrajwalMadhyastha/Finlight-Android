package io.pm.finlight.ui.viewmodel.delegate

import io.pm.finlight.ITransactionRepository
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.domain.usecase.ManageReimbursementUseCase
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionReimbursementDelegate(
    private val coroutineScope: CoroutineScope,
    private val transactionRepository: ITransactionRepository,
    private val manageReimbursementUseCase: ManageReimbursementUseCase,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) {
    private var reimbursementJob: Job? = null
    private var pickerJob: Job? = null

    private val _reimbursementsForCurrentExpense = MutableStateFlow<List<TransactionDetails>>(emptyList())
    val reimbursementsForCurrentExpense: StateFlow<List<TransactionDetails>> = _reimbursementsForCurrentExpense.asStateFlow()

    private val _candidateReimbursements = MutableStateFlow<List<TransactionDetails>>(emptyList())
    val candidateReimbursements: StateFlow<List<TransactionDetails>> = _candidateReimbursements.asStateFlow()

    private val _linkedExpenseForCurrentIncome = MutableStateFlow<TransactionDetails?>(null)
    val linkedExpenseForCurrentIncome: StateFlow<TransactionDetails?> = _linkedExpenseForCurrentIncome.asStateFlow()

    private val _showReimbursementPicker = MutableStateFlow(false)
    val showReimbursementPicker: StateFlow<Boolean> = _showReimbursementPicker.asStateFlow()

    fun canLinkAsReimbursement(
        selectedTransactionIds: Flow<Set<Int>>,
        transactionsForSelectedMonth: Flow<List<TransactionDetails>>,
    ): StateFlow<Boolean> =
        combine(selectedTransactionIds, transactionsForSelectedMonth) { ids, txns ->
            if (ids.size < 2) return@combine false
            val selected = txns.filter { it.transaction.id in ids }
            val expenseCount = selected.count { it.transaction.transactionType == TransactionType.EXPENSE }
            val incomeCount = selected.count { it.transaction.transactionType == TransactionType.INCOME }
            expenseCount == 1 && incomeCount >= 1
        }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), false)

    fun loadReimbursementData(transaction: Transaction) {
        reimbursementJob?.cancel()
        if (transaction.transactionType == TransactionType.EXPENSE) {
            reimbursementJob =
                coroutineScope.launch {
                    transactionRepository.getReimbursementsForExpense(transaction.id).collect { reimbursements ->
                        _reimbursementsForCurrentExpense.value = reimbursements
                    }
                }
        } else if (transaction.transactionType == TransactionType.INCOME && transaction.parentReimbursementId != null) {
            reimbursementJob =
                coroutineScope.launch {
                    transactionRepository.getLinkedExpenseForReimbursement(transaction.id).collect { expense ->
                        _linkedExpenseForCurrentIncome.value = expense
                    }
                }
        }
    }

    fun openReimbursementPicker(expenseId: Int) {
        pickerJob?.cancel()
        pickerJob =
            coroutineScope.launch {
                transactionRepository.getCandidateReimbursements(expenseId).collect { candidates ->
                    _candidateReimbursements.value = candidates
                }
            }
        _showReimbursementPicker.value = true
    }

    fun dismissReimbursementPicker() {
        pickerJob?.cancel()
        pickerJob = null
        _showReimbursementPicker.value = false
    }

    fun linkReimbursement(
        incomeId: Int,
        expenseId: Int,
    ) {
        coroutineScope.launch {
            manageReimbursementUseCase.linkReimbursement(incomeId, expenseId)
            pickerJob?.cancel()
            pickerJob = null
            _showReimbursementPicker.value = false
        }
    }

    fun unlinkReimbursement(incomeId: Int) {
        coroutineScope.launch {
            manageReimbursementUseCase.unlinkReimbursement(incomeId)
        }
    }

    fun linkReimbursementFromSelection(
        selectedIds: Set<Int>,
        allTxns: List<TransactionDetails>,
        onEvent: suspend (String) -> Unit,
        onClearSelection: () -> Unit,
    ) {
        coroutineScope.launch {
            val selected = allTxns.filter { it.transaction.id in selectedIds }
            val expenses = selected.filter { it.transaction.transactionType == TransactionType.EXPENSE }
            val incomes = selected.filter { it.transaction.transactionType == TransactionType.INCOME }
            if (expenses.size != 1 || incomes.isEmpty()) {
                onEvent("Select exactly 1 expense and 1 or more income transactions.")
                return@launch
            }
            val expenseId = expenses.first().transaction.id
            incomes.forEach { income ->
                manageReimbursementUseCase.linkReimbursement(income.transaction.id, expenseId)
            }
            onEvent("${incomes.size} repayment(s) linked.")
            onClearSelection()
        }
    }

    fun resetDetailReimbursementState() {
        reimbursementJob?.cancel()
        reimbursementJob = null
        _reimbursementsForCurrentExpense.value = emptyList()
        _linkedExpenseForCurrentIncome.value = null
    }
}

package io.pm.finlight.ui.viewmodel.delegate

import io.pm.finlight.ITransactionRepository
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.domain.usecase.ManageReimbursementUseCase
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionReimbursementDelegateTest {
    @Mock
    private lateinit var transactionRepository: ITransactionRepository

    @Mock
    private lateinit var manageReimbursementUseCase: ManageReimbursementUseCase

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val dispatcherProvider = TestDispatcherProvider(testDispatcher)

    private lateinit var delegate: TransactionReimbursementDelegate

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        delegate =
            TransactionReimbursementDelegate(
                coroutineScope = testScope,
                transactionRepository = transactionRepository,
                manageReimbursementUseCase = manageReimbursementUseCase,
                dispatcherProvider = dispatcherProvider,
            )
    }

    @Test
    fun `loadReimbursementData loads reimbursements for expense transaction`() =
        runTest(testDispatcher) {
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Team Lunch",
                    categoryId = 1,
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    notes = null,
                    transactionType = TransactionType.EXPENSE,
                )
            val details =
                listOf(
                    TransactionDetails(
                        transaction = Transaction(id = 2, description = "Repayment", categoryId = null, amount = 50.0, date = 1100L, accountId = 1, notes = null, transactionType = TransactionType.INCOME),
                        images = emptyList(),
                        accountName = "Bank",
                        categoryName = null,
                        categoryIconKey = null,
                        categoryColorKey = null,
                        tagNames = null,
                    ),
                )
            whenever(transactionRepository.getReimbursementsForExpense(1)).thenReturn(flowOf(details))

            delegate.loadReimbursementData(expenseTxn)
            testScope.advanceUntilIdle()

            assertEquals(details, delegate.reimbursementsForCurrentExpense.value)
        }

    @Test
    fun `loadReimbursementData loads linked expense for income transaction with parent`() =
        runTest(testDispatcher) {
            val incomeTxn =
                Transaction(
                    id = 2,
                    description = "Repayment",
                    categoryId = null,
                    amount = 50.0,
                    date = 1100L,
                    accountId = 1,
                    notes = null,
                    transactionType = TransactionType.INCOME,
                    parentReimbursementId = 1,
                )
            val expenseDetails =
                TransactionDetails(
                    transaction = Transaction(id = 1, description = "Team Lunch", categoryId = 1, amount = 100.0, date = 1000L, accountId = 1, notes = null, transactionType = TransactionType.EXPENSE),
                    images = emptyList(),
                    accountName = "Bank",
                    categoryName = "Food",
                    categoryIconKey = "icon",
                    categoryColorKey = "red",
                    tagNames = null,
                )
            whenever(transactionRepository.getLinkedExpenseForReimbursement(2)).thenReturn(flowOf(expenseDetails))

            delegate.loadReimbursementData(incomeTxn)
            testScope.advanceUntilIdle()

            assertEquals(expenseDetails, delegate.linkedExpenseForCurrentIncome.value)
        }

    @Test
    fun `open and dismiss reimbursement picker`() =
        runTest(testDispatcher) {
            whenever(transactionRepository.getCandidateReimbursements(1)).thenReturn(flowOf(emptyList()))

            delegate.openReimbursementPicker(1)
            testScope.advanceUntilIdle()
            assertTrue(delegate.showReimbursementPicker.value)

            delegate.dismissReimbursementPicker()
            assertFalse(delegate.showReimbursementPicker.value)
        }

    @Test
    fun `linkReimbursement delegates to manageReimbursementUseCase`() =
        runTest(testDispatcher) {
            delegate.linkReimbursement(incomeId = 2, expenseId = 1)
            testScope.advanceUntilIdle()

            verify(manageReimbursementUseCase).linkReimbursement(incomeId = 2, expenseId = 1)
        }

    @Test
    fun `unlinkReimbursement delegates to manageReimbursementUseCase`() =
        runTest(testDispatcher) {
            delegate.unlinkReimbursement(incomeId = 2)
            testScope.advanceUntilIdle()

            verify(manageReimbursementUseCase).unlinkReimbursement(2)
        }

    @Test
    fun `loadReimbursementData cancels previous collection job when called with a new transaction`() =
        runTest(testDispatcher) {
            val expenseFlow1 = kotlinx.coroutines.flow.MutableSharedFlow<List<TransactionDetails>>()
            val expenseFlow2 = kotlinx.coroutines.flow.MutableSharedFlow<List<TransactionDetails>>()
            val txn1 =
                Transaction(
                    id = 1,
                    description = "Lunch",
                    categoryId = 1,
                    amount = 20.0,
                    date = 100L,
                    accountId = 1,
                    notes = null,
                    transactionType = TransactionType.EXPENSE,
                )
            val txn2 =
                Transaction(
                    id = 2,
                    description = "Dinner",
                    categoryId = 1,
                    amount = 40.0,
                    date = 200L,
                    accountId = 1,
                    notes = null,
                    transactionType = TransactionType.EXPENSE,
                )

            whenever(transactionRepository.getReimbursementsForExpense(1)).thenReturn(expenseFlow1)
            whenever(transactionRepository.getReimbursementsForExpense(2)).thenReturn(expenseFlow2)

            delegate.loadReimbursementData(txn1)
            testScope.advanceUntilIdle()

            delegate.loadReimbursementData(txn2)
            testScope.advanceUntilIdle()

            val details2 =
                listOf(
                    TransactionDetails(
                        transaction =
                            Transaction(
                                id = 3,
                                description = "Repayment",
                                categoryId = null,
                                amount = 40.0,
                                date = 300L,
                                accountId = 1,
                                notes = null,
                                transactionType = TransactionType.INCOME,
                            ),
                        images = emptyList(),
                        accountName = "Bank",
                        categoryName = null,
                        categoryIconKey = null,
                        categoryColorKey = null,
                        tagNames = null,
                    ),
                )
            expenseFlow2.emit(details2)
            testScope.advanceUntilIdle()

            assertEquals(details2, delegate.reimbursementsForCurrentExpense.value)

            expenseFlow1.emit(emptyList())
            testScope.advanceUntilIdle()

            assertEquals(details2, delegate.reimbursementsForCurrentExpense.value)
        }

    @Test
    fun `openReimbursementPicker cancels previous candidate collection job`() =
        runTest(testDispatcher) {
            val candidateFlow1 = kotlinx.coroutines.flow.MutableSharedFlow<List<TransactionDetails>>()
            val candidateFlow2 = kotlinx.coroutines.flow.MutableSharedFlow<List<TransactionDetails>>()

            whenever(transactionRepository.getCandidateReimbursements(1)).thenReturn(candidateFlow1)
            whenever(transactionRepository.getCandidateReimbursements(2)).thenReturn(candidateFlow2)

            delegate.openReimbursementPicker(1)
            testScope.advanceUntilIdle()

            delegate.openReimbursementPicker(2)
            testScope.advanceUntilIdle()

            val candidates2 =
                listOf(
                    TransactionDetails(
                        transaction =
                            Transaction(
                                id = 4,
                                description = "Candidate",
                                categoryId = null,
                                amount = 10.0,
                                date = 400L,
                                accountId = 1,
                                notes = null,
                                transactionType = TransactionType.INCOME,
                            ),
                        images = emptyList(),
                        accountName = "Bank",
                        categoryName = null,
                        categoryIconKey = null,
                        categoryColorKey = null,
                        tagNames = null,
                    ),
                )
            candidateFlow2.emit(candidates2)
            testScope.advanceUntilIdle()

            assertEquals(candidates2, delegate.candidateReimbursements.value)

            candidateFlow1.emit(emptyList())
            testScope.advanceUntilIdle()

            assertEquals(candidates2, delegate.candidateReimbursements.value)
        }

    @Test
    fun `dismissReimbursementPicker cancels candidate collection job`() =
        runTest(testDispatcher) {
            val candidateFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<TransactionDetails>>()
            whenever(transactionRepository.getCandidateReimbursements(1)).thenReturn(candidateFlow)

            delegate.openReimbursementPicker(1)
            testScope.advanceUntilIdle()

            delegate.dismissReimbursementPicker()
            testScope.advanceUntilIdle()

            assertFalse(delegate.showReimbursementPicker.value)

            candidateFlow.emit(
                listOf(
                    TransactionDetails(
                        transaction =
                            Transaction(
                                id = 5,
                                description = "Candidate",
                                categoryId = null,
                                amount = 10.0,
                                date = 500L,
                                accountId = 1,
                                notes = null,
                                transactionType = TransactionType.INCOME,
                            ),
                        images = emptyList(),
                        accountName = "Bank",
                        categoryName = null,
                        categoryIconKey = null,
                        categoryColorKey = null,
                        tagNames = null,
                    ),
                ),
            )
            testScope.advanceUntilIdle()

            assertEquals(emptyList<TransactionDetails>(), delegate.candidateReimbursements.value)
        }

    @Test
    fun `resetDetailReimbursementState cancels reimbursement job and clears state`() =
        runTest(testDispatcher) {
            val expenseFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<TransactionDetails>>()
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Team Lunch",
                    categoryId = 1,
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    notes = null,
                    transactionType = TransactionType.EXPENSE,
                )

            whenever(transactionRepository.getReimbursementsForExpense(1)).thenReturn(expenseFlow)

            delegate.loadReimbursementData(expenseTxn)
            testScope.advanceUntilIdle()

            delegate.resetDetailReimbursementState()
            testScope.advanceUntilIdle()

            assertEquals(emptyList<TransactionDetails>(), delegate.reimbursementsForCurrentExpense.value)

            expenseFlow.emit(
                listOf(
                    TransactionDetails(
                        transaction =
                            Transaction(
                                id = 2,
                                description = "Repayment",
                                categoryId = null,
                                amount = 50.0,
                                date = 1100L,
                                accountId = 1,
                                notes = null,
                                transactionType = TransactionType.INCOME,
                            ),
                        images = emptyList(),
                        accountName = "Bank",
                        categoryName = null,
                        categoryIconKey = null,
                        categoryColorKey = null,
                        tagNames = null,
                    ),
                ),
            )
            testScope.advanceUntilIdle()

            assertEquals(emptyList<TransactionDetails>(), delegate.reimbursementsForCurrentExpense.value)
        }

    @Test
    fun `loadReimbursementData immediately resets previous reimbursement and linked expense states`() =
        runTest(testDispatcher) {
            val expenseFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<TransactionDetails>>()
            val expenseTxn =
                Transaction(
                    id = 1,
                    description = "Lunch",
                    categoryId = 1,
                    amount = 20.0,
                    date = 100L,
                    accountId = 1,
                    notes = null,
                    transactionType = TransactionType.EXPENSE,
                )
            whenever(transactionRepository.getReimbursementsForExpense(1)).thenReturn(expenseFlow)

            delegate.loadReimbursementData(expenseTxn)
            testScope.advanceUntilIdle()

            val details =
                listOf(
                    TransactionDetails(
                        transaction =
                            Transaction(
                                id = 2,
                                description = "Repayment",
                                categoryId = null,
                                amount = 20.0,
                                date = 200L,
                                accountId = 1,
                                notes = null,
                                transactionType = TransactionType.INCOME,
                            ),
                        images = emptyList(),
                        accountName = "Bank",
                        categoryName = null,
                        categoryIconKey = null,
                        categoryColorKey = null,
                        tagNames = null,
                    ),
                )
            expenseFlow.emit(details)
            testScope.advanceUntilIdle()
            assertEquals(details, delegate.reimbursementsForCurrentExpense.value)

            val pendingFlow = kotlinx.coroutines.flow.MutableSharedFlow<List<TransactionDetails>>()
            val nextTxn =
                Transaction(
                    id = 3,
                    description = "Coffee",
                    categoryId = 1,
                    amount = 5.0,
                    date = 300L,
                    accountId = 1,
                    notes = null,
                    transactionType = TransactionType.EXPENSE,
                )
            whenever(transactionRepository.getReimbursementsForExpense(3)).thenReturn(pendingFlow)

            delegate.loadReimbursementData(nextTxn)
            // Immediately empty, before pendingFlow emits
            assertEquals(emptyList<TransactionDetails>(), delegate.reimbursementsForCurrentExpense.value)
            assertNull(delegate.linkedExpenseForCurrentIncome.value)
        }
}

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
}

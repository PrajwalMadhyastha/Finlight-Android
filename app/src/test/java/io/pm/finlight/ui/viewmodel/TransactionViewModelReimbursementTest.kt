package io.pm.finlight.ui.viewmodel

import app.cash.turbine.test
import io.pm.finlight.TransactionDetails
import io.pm.finlight.Transaction
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.Mockito.`when` as whenever

class TransactionViewModelReimbursementTest : TransactionViewModelBaseSetup() {
    @Test
    fun `canLinkAsReimbursement is true only when exactly 1 expense and 1+ incomes are selected`() =
        runTest {
            val expenseTxn =
                TransactionDetails(
                    transaction = Transaction(id = 1, description = "Expense", amount = 1000.0, date = 0L, accountId = 1, categoryId = 1, transactionType = "expense", notes = ""),
                    accountName = "Test", categoryName = "Test", categoryIconKey = "Test", categoryColorKey = "Test", tagNames = "", images = emptyList()
                )
            val incomeTxn1 =
                TransactionDetails(
                    transaction = Transaction(id = 2, description = "Income 1", amount = 500.0, date = 0L, accountId = 1, categoryId = 1, transactionType = "income", notes = ""),
                    accountName = "Test", categoryName = "Test", categoryIconKey = "Test", categoryColorKey = "Test", tagNames = "", images = emptyList()
                )
            val incomeTxn2 =
                TransactionDetails(
                    transaction = Transaction(id = 3, description = "Income 2", amount = 500.0, date = 0L, accountId = 1, categoryId = 1, transactionType = "income", notes = ""),
                    accountName = "Test", categoryName = "Test", categoryIconKey = "Test", categoryColorKey = "Test", tagNames = "", images = emptyList()
                )

            whenever(transactionRepository.getTransactionDetailsForRange(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull()))
                .thenReturn(flowOf(listOf(expenseTxn, incomeTxn1, incomeTxn2)))
            initializeViewModel()

            // Let the flow emit and initialize transactionsForSelectedMonth in BaseSetup
            advanceUntilIdle()

            viewModel.canLinkAsReimbursement.test {
                assertEquals(false, awaitItem()) // Initially false

                // Select just expense
                viewModel.toggleTransactionSelection(1)
                advanceUntilIdle()
                expectNoEvents() // Still false, no new emission

                // Select expense + 1 income -> true
                viewModel.toggleTransactionSelection(2)
                advanceUntilIdle()
                assertEquals(true, awaitItem())

                // Select expense + 2 incomes -> true
                viewModel.toggleTransactionSelection(3)
                advanceUntilIdle()
                expectNoEvents()

                // Deselect expense (only incomes left) -> false
                viewModel.toggleTransactionSelection(1)
                advanceUntilIdle()
                assertEquals(false, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `linkReimbursementFromSelection delegates to repository and clears selection`() =
        runTest {
            val expenseTxn =
                TransactionDetails(
                    transaction = Transaction(id = 1, description = "Expense", amount = 1000.0, date = 0L, accountId = 1, categoryId = 1, transactionType = "expense", notes = ""),
                    accountName = "Test", categoryName = "Test", categoryIconKey = "Test", categoryColorKey = "Test", tagNames = "", images = emptyList()
                )
            val incomeTxn =
                TransactionDetails(
                    transaction = Transaction(id = 2, description = "Income", amount = 500.0, date = 0L, accountId = 1, categoryId = 1, transactionType = "income", notes = ""),
                    accountName = "Test", categoryName = "Test", categoryIconKey = "Test", categoryColorKey = "Test", tagNames = "", images = emptyList()
                )

            whenever(transactionRepository.getTransactionDetailsForRange(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull()))
                .thenReturn(flowOf(listOf(expenseTxn, incomeTxn)))
            initializeViewModel()
            backgroundScope.launch { viewModel.transactionsForSelectedMonth.collect() }
            advanceUntilIdle()

            viewModel.toggleTransactionSelection(1)
            viewModel.toggleTransactionSelection(2)
            advanceUntilIdle()

            viewModel.uiEvent.test {
                viewModel.linkReimbursementFromSelection()
                advanceUntilIdle()

                verify(transactionRepository).linkReimbursement(incomeId = 2, expenseId = 1)
                assertEquals("1 repayment(s) linked.", awaitItem())
                assertFalse(viewModel.isSelectionModeActive.value)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `openReimbursementPicker fetches candidates and shows sheet`() =
        runTest {
            val candidates =
                listOf(
                    TransactionDetails(
                        transaction = Transaction(id = 2, description = "Income", amount = 500.0, date = 0L, accountId = 1, categoryId = 1, transactionType = "income", notes = ""),
                        accountName = "Test", categoryName = "Test", categoryIconKey = "Test", categoryColorKey = "Test", tagNames = "", images = emptyList()
                    )
                )
            whenever(transactionRepository.getCandidateReimbursements(1)).thenReturn(flowOf(candidates))
            initializeViewModel()

            viewModel.openReimbursementPicker(1)
            advanceUntilIdle()

            assertTrue(viewModel.showReimbursementPicker.value)
            assertEquals(candidates, viewModel.candidateReimbursements.value)
        }

    @Test
    fun `linkReimbursement hides sheet and calls repository`() =
        runTest {
            whenever(transactionRepository.getCandidateReimbursements(1)).thenReturn(flowOf(emptyList()))
            initializeViewModel()

            viewModel.openReimbursementPicker(1)
            advanceUntilIdle()
            assertTrue(viewModel.showReimbursementPicker.value)

            viewModel.linkReimbursement(incomeId = 2, expenseId = 1)
            advanceUntilIdle()

            verify(transactionRepository).linkReimbursement(2, 1)
            assertFalse(viewModel.showReimbursementPicker.value)
        }
}

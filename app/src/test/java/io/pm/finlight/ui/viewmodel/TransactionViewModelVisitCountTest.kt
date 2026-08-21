package io.pm.finlight.ui.viewmodel

import app.cash.turbine.test
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.Mockito.`when` as whenever

/**
 * Unit tests for the visit count logic applied to [TransactionViewModel].
 *
 * Covers scenarios:
 *  1. When a transaction is renamed, currentDescription is queried so that all transactions
 *     under the same renamed merchant are consolidated into a cumulative visit count.
 *  2. When originalDescription is null, currentDescription is used as expected.
 *  3. Income transactions are NOT excluded — count is loaded so the UI can show
 *     "N credits" instead of suppressing the chip entirely.
 *  4. visitCount state is correctly populated from the repository value.
 */
class TransactionViewModelVisitCountTest : TransactionViewModelBaseSetup() {
    // ---------------------------------------------------------------------------
    // UT-1: currentDescription is used so renamed merchants consolidate visit count
    // ---------------------------------------------------------------------------

    @Test
    fun `loadTransactionForDetailScreen expense uses currentDescription for visit count query`() =
        runTest {
            // ARRANGE — expense with both original (raw SMS) and current (renamed) descriptions
            val txnId = 42
            val originalDesc = "SWIGGY INDIA" // raw SMS text
            val currentDesc = "Food in office" // user-renamed / rule-renamed merchant

            val expenseTxn =
                Transaction(
                    id = txnId,
                    description = currentDesc,
                    originalDescription = originalDesc,
                    amount = 350.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = null,
                )

            whenever(transactionRepository.getTransactionById(txnId)).thenReturn(flowOf(expenseTxn))
            whenever(transactionRepository.getTagsForTransaction(txnId)).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getImagesForTransaction(txnId)).thenReturn(flowOf(emptyList()))
            whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
            whenever(transactionRepository.getReimbursementsForExpense(txnId)).thenReturn(flowOf(emptyList()))

            val captor = argumentCaptor<String>()
            whenever(transactionRepository.getTransactionCountForMerchant(anyString()))
                .thenReturn(flowOf(3))

            // ACT
            viewModel.loadTransactionForDetailScreen(txnId)
            advanceUntilIdle()

            // ASSERT — the query must use currentDescription to consolidate all visits
            verify(transactionRepository).getTransactionCountForMerchant(captor.capture())
            assertEquals(
                "Visit count query should use currentDescription to consolidate renamed merchants",
                currentDesc,
                captor.firstValue,
            )
        }

    // ---------------------------------------------------------------------------
    // UT-2: Uses currentDescription when originalDescription is null
    // ---------------------------------------------------------------------------

    @Test
    fun `loadTransactionForDetailScreen expense uses currentDescription when originalDescription is null`() =
        runTest {
            // ARRANGE — manually-entered expense has no originalDescription
            val txnId = 43
            val currentDesc = "Local Bakery"

            val expenseTxn =
                Transaction(
                    id = txnId,
                    description = currentDesc,
                    // no SMS source
                    originalDescription = null,
                    amount = 80.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 2,
                    transactionType = TransactionType.EXPENSE,
                    notes = null,
                )

            whenever(transactionRepository.getTransactionById(txnId)).thenReturn(flowOf(expenseTxn))
            whenever(transactionRepository.getTagsForTransaction(txnId)).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getImagesForTransaction(txnId)).thenReturn(flowOf(emptyList()))
            whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
            whenever(transactionRepository.getReimbursementsForExpense(txnId)).thenReturn(flowOf(emptyList()))

            val captor = argumentCaptor<String>()
            whenever(transactionRepository.getTransactionCountForMerchant(anyString()))
                .thenReturn(flowOf(1))

            // ACT
            viewModel.loadTransactionForDetailScreen(txnId)
            advanceUntilIdle()

            // ASSERT — uses currentDescription
            verify(transactionRepository).getTransactionCountForMerchant(captor.capture())
            assertEquals(
                "Visit count query should use currentDescription",
                currentDesc,
                captor.firstValue,
            )
        }

    // ---------------------------------------------------------------------------
    // UT-3: Income transactions still load visit count (for "N credits" chip)
    // ---------------------------------------------------------------------------

    @Test
    fun `loadTransactionForDetailScreen income still loads visit count for credits chip`() =
        runTest {
            // ARRANGE — income (salary credit); the chip should show "N credits"
            val txnId = 44
            val originalDesc = "HDFC BANK SALARY"
            val currentDesc = "Salary"
            val expectedCount = 5

            val incomeTxn =
                Transaction(
                    id = txnId,
                    description = currentDesc,
                    originalDescription = originalDesc,
                    amount = 50000.0,
                    date = 0L,
                    accountId = 2,
                    categoryId = null,
                    transactionType = TransactionType.INCOME,
                    notes = null,
                    parentReimbursementId = null,
                )

            whenever(transactionRepository.getTransactionById(txnId)).thenReturn(flowOf(incomeTxn))
            whenever(transactionRepository.getTagsForTransaction(txnId)).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getImagesForTransaction(txnId)).thenReturn(flowOf(emptyList()))
            whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
            // Income with no parentReimbursementId — no reimbursement side-effect
            whenever(transactionRepository.getTransactionCountForMerchant(anyString()))
                .thenReturn(flowOf(expectedCount))

            // ACT
            viewModel.loadTransactionForDetailScreen(txnId)
            advanceUntilIdle()

            // ASSERT — visitCount must be populated (NOT suppressed to 0 for income)
            viewModel.visitCount.test {
                val count = awaitItem()
                assertEquals(
                    "Income transactions should still populate visitCount so the 'N credits' chip is shown",
                    expectedCount,
                    count,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // UT-4: visitCount reflects the value emitted by the repository
    // ---------------------------------------------------------------------------

    @Test
    fun `loadTransactionForDetailScreen expense populates visitCount state correctly`() =
        runTest {
            // ARRANGE
            val txnId = 45
            val desc = "Starbucks"
            val expectedCount = 7

            val expenseTxn =
                Transaction(
                    id = txnId,
                    description = desc,
                    originalDescription = null,
                    amount = 250.0,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    transactionType = TransactionType.EXPENSE,
                    notes = null,
                )

            whenever(transactionRepository.getTransactionById(txnId)).thenReturn(flowOf(expenseTxn))
            whenever(transactionRepository.getTagsForTransaction(txnId)).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getImagesForTransaction(txnId)).thenReturn(flowOf(emptyList()))
            whenever(smsRepository.getSmsDetailsById(anyLong())).thenReturn(null)
            whenever(transactionRepository.getReimbursementsForExpense(txnId)).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getTransactionCountForMerchant(anyString()))
                .thenReturn(flowOf(expectedCount))

            // ACT
            viewModel.loadTransactionForDetailScreen(txnId)
            advanceUntilIdle()

            // ASSERT
            viewModel.visitCount.test {
                assertEquals(expectedCount, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

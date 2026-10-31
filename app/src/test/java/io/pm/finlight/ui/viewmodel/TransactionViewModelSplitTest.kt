package io.pm.finlight.ui.viewmodel

import app.cash.turbine.test
import io.pm.finlight.SplitTransaction
import io.pm.finlight.SplitTransactionDetails
import io.pm.finlight.Transaction
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

class TransactionViewModelSplitTest : TransactionViewModelBaseSetup() {
    @Test
    fun `unsplitTransaction correctly calls repository`() =
        runTest {
            val transaction =
                Transaction(id = 1, description = "Split", isSplit = true, originalDescription = "Original Desc", amount = 100.0, date = 0L, accountId = 1, categoryId = null, notes = null)

            viewModel.unsplitTransaction(transaction)
            advanceUntilIdle()

            verify(splitTransactionRepository).unmarkSplit(1, "Original Desc")
        }

    @Test
    fun `saveTransactionSplits calls repository and executes callback`() =
        runTest {
            val transaction = Transaction(id = 1, description = "Test", amount = 100.0, date = 0L, accountId = 1, categoryId = null, notes = null)
            whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(transaction))
            val splitItems = listOf(SplitItem(id = 1, amount = "50.0", category = null, notes = "Notes"))
            var completeCalled = false

            viewModel.saveTransactionSplits(1, splitItems) {
                completeCalled = true
            }
            advanceUntilIdle()

            verify(splitTransactionRepository).saveSplits(eq(1), any())
            assertTrue(completeCalled)
        }

    @Test
    fun `getSplitDetailsForTransaction calls repository`() =
        runTest {
            // Arrange
            val transactionId = 1
            val mockSplits =
                listOf(
                    SplitTransactionDetails(SplitTransaction(1, 1, 50.0, 1, null), "Cat1", "", ""),
                )
            whenever(splitTransactionRepository.getSplitsForParent(transactionId)).thenReturn(flowOf(mockSplits))

            // Act
            val resultFlow = viewModel.getSplitDetailsForTransaction(transactionId)

            // Assert
            resultFlow.test {
                assertEquals("Flow should emit repository data", mockSplits, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(splitTransactionRepository).getSplitsForParent(transactionId)
        }

    @Test
    fun `saveTransactionSplits passes false and does not throw on error`() =
        runTest {
            whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(null))
            val splitItems = listOf(SplitItem(id = 1, amount = "50.0", category = null, notes = "Notes"))
            var result: Boolean? = null

            viewModel.saveTransactionSplits(1, splitItems) { success ->
                result = success
            }
            advanceUntilIdle()

            assertEquals(false, result)
        }

    @Test
    fun `unsplitTransaction error emits message to uiEvent`() =
        runTest {
            val transaction =
                Transaction(id = 1, description = "Split", isSplit = true, originalDescription = "Original Desc", amount = 100.0, date = 0L, accountId = 1, categoryId = null, notes = null)
            org.mockito.Mockito.doThrow(RuntimeException("DB failure"))
                .`when`(splitTransactionRepository).unmarkSplit(1, "Original Desc")

            viewModel.uiEvent.test {
                viewModel.unsplitTransaction(transaction)
                advanceUntilIdle()

                assertEquals("Failed to un-split transaction.", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

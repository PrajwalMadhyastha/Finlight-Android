package io.pm.finlight.ui.viewmodel

import app.cash.turbine.test
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when` as whenever

class TransactionViewModelAliasTest : TransactionViewModelBaseSetup() {
    @Test
    fun `findTransactionDetailsById emits description from repository directly without in-memory override`() =
        runTest {
            val transactionId = 1
            val original = "VIJAYALAKSH"
            val currentDesc = "VIJAYALAKSH"

            val transaction =
                Transaction(
                    id = transactionId,
                    description = currentDesc,
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    originalDescription = original,
                    notes = null,
                )
            val details = TransactionDetails(transaction, emptyList(), null, null, null, null, null)

            whenever(transactionRepository.getTransactionDetailsById(transactionId)).thenReturn(flowOf(details))

            initializeViewModel()

            viewModel.findTransactionDetailsById(transactionId).test {
                val result = awaitItem()
                assertEquals("Description should come directly from repository", currentDesc, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `findTransactionDetailsById preserves manually overridden description from repository`() =
        runTest {
            val transactionId = 1
            val original = "VIJAYALAKSH"
            val currentDesc = "Food in Office" // Manually modified by user

            val transaction =
                Transaction(
                    id = transactionId,
                    description = currentDesc,
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    originalDescription = original,
                    notes = null,
                )
            val details = TransactionDetails(transaction, emptyList(), null, null, null, null, null)

            whenever(transactionRepository.getTransactionDetailsById(transactionId)).thenReturn(flowOf(details))

            initializeViewModel()

            viewModel.findTransactionDetailsById(transactionId).test {
                val result = awaitItem()
                assertEquals("Description should match repository", currentDesc, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `findTransactionDetailsById emits alias description when repository stores alias`() =
        runTest {
            val transactionId = 1
            val original = "VIJAYALAKSH"
            val currentDesc = "Badminton" // Already alias in DB
            val alias = "Badminton"

            val transaction =
                Transaction(
                    id = transactionId,
                    description = currentDesc,
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    originalDescription = original,
                    notes = null,
                )
            val details = TransactionDetails(transaction, emptyList(), null, null, null, null, null)

            whenever(transactionRepository.getTransactionDetailsById(transactionId)).thenReturn(flowOf(details))

            initializeViewModel()

            viewModel.findTransactionDetailsById(transactionId).test {
                val result = awaitItem()
                assertEquals("Description should match repository", alias, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `findTransactionDetailsById emits description when original is null`() =
        runTest {
            val transactionId = 1
            val original = null
            val currentDesc = "Food in Office"

            val transaction =
                Transaction(
                    id = transactionId,
                    description = currentDesc,
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    originalDescription = original,
                    notes = null,
                )
            val details = TransactionDetails(transaction, emptyList(), null, null, null, null, null)

            whenever(transactionRepository.getTransactionDetailsById(transactionId)).thenReturn(flowOf(details))

            initializeViewModel()

            viewModel.findTransactionDetailsById(transactionId).test {
                val result = awaitItem()
                assertEquals("Description should match repository", currentDesc, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

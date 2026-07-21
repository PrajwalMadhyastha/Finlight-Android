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
    fun `findTransactionDetailsById applies alias when current description matches original`() =
        runTest {
            val transactionId = 1
            val original = "VIJAYALAKSH"
            val currentDesc = "VIJAYALAKSH"
            val alias = "Food in Office"

            val transaction =
                Transaction(
                    id = transactionId,
                    description = currentDesc,
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    originalDescription = original,
                    notes = null
                )
            val details = TransactionDetails(transaction, emptyList(), null, null, null, null, null)

            whenever(transactionRepository.getTransactionDetailsById(transactionId)).thenReturn(flowOf(details))
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(mapOf(original.lowercase() to alias)))

            initializeViewModel()

            viewModel.findTransactionDetailsById(transactionId).test {
                val result = awaitItem()
                assertEquals("Alias should be applied because description matches original", alias, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `findTransactionDetailsById ignores alias when current description was manually changed`() =
        runTest {
            val transactionId = 1
            val original = "VIJAYALAKSH"
            val currentDesc = "Food in Office" // Manually modified by user
            val alias = "Badminton" // Existing rule for VIJAYALAKSH, e.g. from mistake

            val transaction =
                Transaction(
                    id = transactionId,
                    description = currentDesc,
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    originalDescription = original,
                    notes = null
                )
            val details = TransactionDetails(transaction, emptyList(), null, null, null, null, null)

            whenever(transactionRepository.getTransactionDetailsById(transactionId)).thenReturn(flowOf(details))
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(mapOf(original.lowercase() to alias)))

            initializeViewModel()

            viewModel.findTransactionDetailsById(transactionId).test {
                val result = awaitItem()
                assertEquals("Alias should NOT be applied because user manually overrode description", currentDesc, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `findTransactionDetailsById retains alias when current description matches alias`() =
        runTest {
            val transactionId = 1
            val original = "VIJAYALAKSH"
            val currentDesc = "Badminton" // Already alias
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
                    notes = null
                )
            val details = TransactionDetails(transaction, emptyList(), null, null, null, null, null)

            whenever(transactionRepository.getTransactionDetailsById(transactionId)).thenReturn(flowOf(details))
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(mapOf(original.lowercase() to alias)))

            initializeViewModel()

            viewModel.findTransactionDetailsById(transactionId).test {
                val result = awaitItem()
                assertEquals("Alias should be retained", alias, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `findTransactionDetailsById applies alias when original is null and currentDesc matches key`() =
        runTest {
            val transactionId = 1
            val original = null
            val currentDesc = "Food in Office"
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
                    notes = null
                )
            val details = TransactionDetails(transaction, emptyList(), null, null, null, null, null)

            whenever(transactionRepository.getTransactionDetailsById(transactionId)).thenReturn(flowOf(details))
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(mapOf(currentDesc.lowercase() to alias)))

            initializeViewModel()

            viewModel.findTransactionDetailsById(transactionId).test {
                val result = awaitItem()
                assertEquals("Alias should be applied", alias, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

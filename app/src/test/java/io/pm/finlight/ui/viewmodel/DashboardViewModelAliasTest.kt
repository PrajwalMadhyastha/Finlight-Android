package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when` as whenever
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DashboardViewModelAliasTest : DashboardViewModelTest() {
    @Test
    fun `recentTransactionsState applies alias when current description matches original`() =
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

            whenever(transactionRepository.recentTransactions).thenReturn(flowOf(listOf(details)))
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(mapOf(original.lowercase() to alias)))

            // Re-initialize viewmodel to pick up mocked flows
            initializeViewModel()

            viewModel.recentTransactions.test {
                val state = awaitItem()
                val result = state.firstOrNull()
                assertEquals("Alias should be applied", alias, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recentTransactionsState ignores alias when current description was manually changed`() =
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

            whenever(transactionRepository.recentTransactions).thenReturn(flowOf(listOf(details)))
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(mapOf(original.lowercase() to alias)))

            initializeViewModel()

            viewModel.recentTransactions.test {
                val state = awaitItem()
                val result = state.firstOrNull()
                assertEquals("Alias should NOT be applied", currentDesc, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recentTransactionsState applies alias when original is null and currentDesc matches key`() =
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

            whenever(transactionRepository.recentTransactions).thenReturn(flowOf(listOf(details)))
            whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(mapOf(currentDesc.lowercase() to alias)))

            initializeViewModel()

            viewModel.recentTransactions.test {
                val state = awaitItem()
                val result = state.firstOrNull()
                assertEquals("Alias should be applied", alias, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

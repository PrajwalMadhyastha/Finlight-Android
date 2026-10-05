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
    fun `recentTransactions emits description from repository directly without in-memory override`() =
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

            whenever(transactionRepository.recentTransactions).thenReturn(flowOf(listOf(details)))

            // Re-initialize viewmodel to pick up mocked flows
            initializeViewModel()

            viewModel.recentTransactions.test {
                val state = awaitItem()
                val result = state.firstOrNull()
                assertEquals("Description should come directly from repository", currentDesc, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recentTransactions preserves manually changed description from repository`() =
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

            whenever(transactionRepository.recentTransactions).thenReturn(flowOf(listOf(details)))

            initializeViewModel()

            viewModel.recentTransactions.test {
                val state = awaitItem()
                val result = state.firstOrNull()
                assertEquals("Description should match repository", currentDesc, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recentTransactions emits description when original is null`() =
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

            whenever(transactionRepository.recentTransactions).thenReturn(flowOf(listOf(details)))

            initializeViewModel()

            viewModel.recentTransactions.test {
                val state = awaitItem()
                val result = state.firstOrNull()
                assertEquals("Description should match repository", currentDesc, result?.transaction?.description)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

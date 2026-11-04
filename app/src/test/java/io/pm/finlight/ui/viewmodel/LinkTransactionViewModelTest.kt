// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/LinkTransactionViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// Decoupled from IRecurringTransactionRepository to test isolated SMS linking.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class LinkTransactionViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var transactionRepository: TransactionRepository

    private lateinit var viewModel: LinkTransactionViewModel

    private val potentialTxn =
        PotentialTransaction(
            sourceSmsId = 123L,
            smsSender = "TestSender",
            amount = 100.0,
            transactionType = "expense",
            merchantName = "Test Merchant",
            originalMessage = "Test message",
            sourceSmsHash = "test_hash_123",
        )

    @Before
    override fun setup() {
        super.setup()
    }

    @Test
    fun `init calls findMatches and populates linkableTransactions`() =
        runTest {
            // Arrange
            val mockTransactions =
                listOf(
                    Transaction(id = 1, description = "Txn 1", amount = 50.0, date = 0L, accountId = 1, categoryId = 1, notes = null),
                    Transaction(id = 2, description = "Txn 2", amount = 50.0, date = 0L, accountId = 1, categoryId = 1, notes = null),
                )
            `when`(transactionRepository.getAllTransactionsForRange(anyLong(), anyLong())).thenReturn(flowOf(mockTransactions))

            // Act
            viewModel = LinkTransactionViewModel(transactionRepository, potentialTxn, TestDispatcherProvider(testDispatcher))

            // Assert
            viewModel.linkableTransactions.test {
                assertEquals(mockTransactions, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(transactionRepository).getAllTransactionsForRange(anyLong(), anyLong())
        }

    @Test
    fun `linkTransaction calls repository setSmsHash and completes`() =
        runTest {
            // Arrange
            `when`(transactionRepository.getAllTransactionsForRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
            viewModel = LinkTransactionViewModel(transactionRepository, potentialTxn, TestDispatcherProvider(testDispatcher))
            val selectedTxnId = 99
            var onCompleteCalled = false

            // Act
            viewModel.linkTransaction(selectedTxnId) { onCompleteCalled = true }

            // Assert
            verify(transactionRepository).setSmsHash(selectedTxnId, potentialTxn.sourceSmsHash!!)
            assertTrue("onComplete lambda should be called", onCompleteCalled)
        }

    @Test
    fun `linkTransaction handles null sourceSmsHash gracefully`() =
        runTest {
            // Arrange
            val txnWithoutHash = potentialTxn.copy(sourceSmsHash = null)
            `when`(transactionRepository.getAllTransactionsForRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
            viewModel = LinkTransactionViewModel(transactionRepository, txnWithoutHash, TestDispatcherProvider(testDispatcher))
            val selectedTxnId = 99
            var onCompleteCalled = false

            // Act
            viewModel.linkTransaction(selectedTxnId) { onCompleteCalled = true }

            // Assert
            verify(transactionRepository, never()).setSmsHash(anyLong().toInt(), org.mockito.Mockito.anyString())
            assertTrue("onComplete lambda should be called", onCompleteCalled)
        }

    @Test
    fun `linkTransaction on repository failure emits error to uiEvent and calls onComplete with false`() =
        runTest {
            `when`(transactionRepository.getAllTransactionsForRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
            viewModel = LinkTransactionViewModel(transactionRepository, potentialTxn, TestDispatcherProvider(testDispatcher))
            val selectedTxnId = 99
            org.mockito.Mockito.doThrow(RuntimeException("Linking failed")).`when`(transactionRepository).setSmsHash(selectedTxnId, potentialTxn.sourceSmsHash!!)
            var completionResult: Boolean? = null

            viewModel.uiEvent.test {
                viewModel.linkTransaction(selectedTxnId) { success -> completionResult = success }
                val event = awaitItem()
                assertEquals("Error linking transaction: Linking failed", event)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(false, completionResult)
        }
}

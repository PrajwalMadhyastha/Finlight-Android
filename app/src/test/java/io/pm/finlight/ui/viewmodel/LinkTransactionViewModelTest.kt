// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/LinkTransactionViewModelTest.kt
// REASON: NEW FILE - Unit tests for the refactored LinkTransactionViewModel. This
// test suite verifies the ViewModel's logic for finding linkable transactions,
// linking a transaction, and handling the "remind tomorrow" action.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.RecurringTransactionDao
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class LinkTransactionViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    @Mock
    private lateinit var recurringTransactionDao: RecurringTransactionDao

    @Captor
    private lateinit var intCaptor: ArgumentCaptor<Int>

    @Captor
    private lateinit var longCaptor: ArgumentCaptor<Long>

    private lateinit var viewModel: LinkTransactionViewModel

    private val potentialTxn = PotentialTransaction(
        sourceSmsId = 123L,
        smsSender = "TestSender",
        amount = 100.0,
        transactionType = "expense",
        merchantName = "Test Merchant",
        originalMessage = "Test message",
        sourceSmsHash = "test_hash_123"
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init calls findMatches and populates linkableTransactions`() = runTest {
        // Arrange
        val mockTransactions = listOf(
            Transaction(id = 1, description = "Txn 1", amount = 50.0, date = 0L, accountId = 1, categoryId = 1, notes = null),
            Transaction(id = 2, description = "Txn 2", amount = 50.0, date = 0L, accountId = 1, categoryId = 1, notes = null)
        )
        `when`(transactionRepository.getAllTransactionsForRange(anyLong(), anyLong())).thenReturn(flowOf(mockTransactions))

        // Act
        viewModel = LinkTransactionViewModel(transactionRepository, recurringTransactionDao, potentialTxn)

        // Assert
        viewModel.linkableTransactions.test {
            assertEquals(mockTransactions, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionRepository).getAllTransactionsForRange(anyLong(), anyLong())
    }

    @Test
    fun `linkTransaction calls repository and dao methods correctly`() = runTest {
        // Arrange
        `when`(transactionRepository.getAllTransactionsForRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
        viewModel = LinkTransactionViewModel(transactionRepository, recurringTransactionDao, potentialTxn)
        val selectedTxnId = 99
        var onCompleteCalled = false

        // Act
        viewModel.linkTransaction(selectedTxnId) { onCompleteCalled = true }

        // Assert
        verify(transactionRepository).setSmsHash(selectedTxnId, potentialTxn.sourceSmsHash!!)
        verify(recurringTransactionDao).updateLastRunDate(intCaptor.capture(), longCaptor.capture())

        val capturedRuleId = intCaptor.value
        val capturedTimestamp = longCaptor.value

        assertEquals(potentialTxn.sourceSmsId.toInt(), capturedRuleId)
        assertTrue("Timestamp should be recent", System.currentTimeMillis() - capturedTimestamp < 1000)
        assertTrue("onComplete lambda should be called", onCompleteCalled)
    }

    @Test
    fun `remindTomorrow calls dao with yesterday's date`() = runTest {
        // Arrange
        `when`(transactionRepository.getAllTransactionsForRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))
        viewModel = LinkTransactionViewModel(transactionRepository, recurringTransactionDao, potentialTxn)
        var onCompleteCalled = false

        // Act
        viewModel.remindTomorrow { onCompleteCalled = true }

        // Assert
        verify(recurringTransactionDao).updateLastRunDate(intCaptor.capture(), longCaptor.capture())
        val capturedRuleId = intCaptor.value
        val capturedTimestamp = longCaptor.value

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        val oneSecondInMillis = 1000

        assertEquals(potentialTxn.sourceSmsId.toInt(), capturedRuleId)
        assertTrue(
            "Captured timestamp should be approximately yesterday",
            kotlin.math.abs(capturedTimestamp - yesterday) < oneSecondInMillis
        )
        assertTrue("onComplete lambda should be called", onCompleteCalled)
    }
}
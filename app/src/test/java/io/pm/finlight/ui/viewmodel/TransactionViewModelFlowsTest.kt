package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.core.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.*
import io.pm.finlight.data.model.MerchantPrediction
import io.pm.finlight.ui.components.ShareableField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import java.lang.RuntimeException
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds
import org.mockito.Mockito.`when` as whenever

class TransactionViewModelFlowsTest : TransactionViewModelBaseSetup() {

    @Test
        fun `transactionsForSelectedMonth flow emits data from repository and applies aliases`() =
            runTest {
                // ARRANGE
                val transaction =
                    Transaction(
                        id = 1,
                        description = "amzn",
                        originalDescription = "amzn",
                        amount = 100.0,
                        date = 1L,
                        accountId = 1,
                        categoryId = 1,
                        notes = null,
                    )
                val transactionDetails = TransactionDetails(transaction, emptyList(), "Account", "Category", null, null, null)
                val aliases = mapOf("amzn" to "Amazon")
                whenever(
                    transactionRepository.getTransactionDetailsForRange(any<Long>(), any<Long>(), anyOrNull(), anyOrNull(), anyOrNull()),
                ).thenReturn(flowOf(listOf(transactionDetails)))
                whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(aliases))

                // ACT
                initializeViewModel()

                // ASSERT
                viewModel.transactionsForSelectedMonth.test {
                    advanceUntilIdle()
                    val result = expectMostRecentItem()
                    assertEquals(1, result.size)
                    assertEquals("Amazon", result.first().transaction.description)
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `financial summary flows correctly update from repository`() =
            runTest {
                // ARRANGE
                val summary = FinancialSummary(totalIncome = 10000.50, totalExpenses = 5000.25)
                whenever(transactionRepository.getFinancialSummaryForRangeFlow(anyLong(), anyLong())).thenReturn(flowOf(summary))

                // ACT
                initializeViewModel()

                // ASSERT
                viewModel.monthlyIncome.test {
                    assertEquals(10000.50, awaitItem(), 0.01)
                    cancelAndIgnoreRemainingEvents()
                }
                viewModel.monthlyExpenses.test {
                    assertEquals(5000.25, awaitItem(), 0.01)
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `dataLoading failure emits emptyList and sends uiEvent`() =
            runTest {
                // ARRANGE
                val errorFlow = flow<List<TransactionDetails>> { throw RuntimeException("DB error") }
                whenever(transactionRepository.getTransactionDetailsForRange(anyLong(), anyLong(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(errorFlow)

                // FIX: Re-initialize the ViewModel BEFORE the test block.
                // This ensures the current 'viewModel' instance uses the mock above.
                initializeViewModel()

                // ACT & ASSERT
                viewModel.uiEvent.test {
                    // Launch a collector for the lazy flow. This is what triggers
                    // the upstream flow, causing the exception to be thrown and the `.catch`
                    // block to execute. Using `backgroundScope` prevents this from blocking.
                    val collectorJob =
                        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                            viewModel.transactionsForSelectedMonth.collect {
                                // This empty lambda is required to satisfy the `collect` function's signature.
                            }
                        }

                    // Now that collection has started, the error is triggered, caught,
                    // and the event is sent. We can now safely await it.
                    assertEquals("Failed to load transactions.", awaitItem())

                    // We can also check the final state of the other flow.
                    assertEquals(emptyList<TransactionDetails>(), viewModel.transactionsForSelectedMonth.value)

                    // Clean up the collector job and the Turbine test.
                    collectorJob.cancel()
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `onUserManuallySelectedCategory stops suggestedCategory flow`() =
            runTest {
                // Arrange
                val foodCategory = Category(1, "Food & Drinks", "icon", "color")
                whenever(categoryRepository.allCategories).thenReturn(flowOf(listOf(foodCategory)))
                initializeViewModel()

                viewModel.suggestedCategory.test(timeout = 5.seconds) {
                    assertNull("Initial suggestion should be null", awaitItem())

                    // Act 1: Type a keyword, get suggestion
                    viewModel.onAddTransactionDescriptionChanged("Coffee")
                    advanceTimeBy(500)
                    assertEquals("Food & Drinks", awaitItem()?.name)

                    // Act 2: Trigger manual select
                    viewModel.onUserManuallySelectedCategory()
                    advanceUntilIdle()

                    // Act 3: Type another keyword
                    viewModel.onAddTransactionDescriptionChanged("Burger")
                    advanceTimeBy(500)

                    // Assert 3: Suggestion should be null because manual select is true
                    assertEquals(null, awaitItem())

                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `findTransactionDetailsById applies aliases`() =
            runTest {
                // Arrange
                val transactionId = 1
                val transaction =
                    Transaction(
                        id = transactionId,
                        description = "amzn",
                        originalDescription = "amzn",
                        amount = 100.0,
                        date = 1L,
                        accountId = 1,
                        categoryId = 1,
                        notes = null,
                    )
                val mockDetails = TransactionDetails(transaction, emptyList(), "Account", "Category", null, null, null)
                val aliases = mapOf("amzn" to "Amazon")

                whenever(transactionRepository.getTransactionDetailsById(transactionId)).thenReturn(flowOf(mockDetails))
                whenever(merchantRenameRuleRepository.getAliasesAsMap()).thenReturn(flowOf(aliases))

                // --- THIS IS THE FIX ---
                // Re-initialize the ViewModel *after* the test-specific mock for getAliasesAsMap is set.
                // This ensures the `merchantAliases` StateFlow in the ViewModel's init block
                // collects the correct map (with "Amazon") instead of the default `emptyMap()`.
                initializeViewModel()

                // Act
                viewModel.findTransactionDetailsById(transactionId).test {
                    // Assert
                    val result = awaitItem()
                    assertNotNull(result)
                    assertEquals("Amazon", result!!.transaction.description)
                    cancelAndIgnoreRemainingEvents()
                }
                verify(transactionRepository).getTransactionDetailsById(transactionId)
            }

    @Test
        fun `setSelectedMonth updates selectedMonth flow`() =
            runTest {
                // Arrange
                val newCalendar = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }

                // Act & Assert
                viewModel.selectedMonth.test {
                    val initialMonth = awaitItem().get(Calendar.MONTH)

                    viewModel.setSelectedMonth(newCalendar)

                    val newMonth = awaitItem().get(Calendar.MONTH)
                    assertEquals(newCalendar.get(Calendar.MONTH), newMonth)
                    assertNotEquals(initialMonth, newMonth)
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `isPrivacyModeEnabled flow emits value from settingsRepository`() =
            runTest {
                // Arrange
                val privacyFlow = flowOf(true)
                whenever(settingsRepository.getPrivacyModeEnabled()).thenReturn(privacyFlow)
                initializeViewModel() // Re-initialize to pick up the new mock

                // Act & Assert
                viewModel.isPrivacyModeEnabled.test {
                    assertTrue(awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
                // --- THE FIX ---
                // Verify times(2) because it was called once in setup() and once again
                // in this test's initializeViewModel() call.
                verify(settingsRepository, times(2)).getPrivacyModeEnabled()
            }

    @Test
        fun `recentManualTransactions emits data from repository`() =
            runTest {
                // Arrange
                val transaction =
                    Transaction(
                        id = 1,
                        description = "Coffee",
                        amount = 50.0,
                        date = 0L,
                        accountId = 1,
                        categoryId = 1,
                        notes = null,
                        source = "Manual Entry",
                    )
                val details = TransactionDetails(transaction, emptyList(), "Cash", "Food", "food", "green", null)
                whenever(transactionRepository.getRecentManualTransactions(10)).thenReturn(flowOf(listOf(details)))
                initializeViewModel()

                // Act & Assert
                viewModel.recentManualTransactions.test {
                    val result = awaitItem()
                    assertEquals(1, result.size)
                    assertEquals("Coffee", result.first().transaction.description)
                    cancelAndIgnoreRemainingEvents()
                }
            }

    @Test
        fun `historyManualTransactions emits data from repository`() =
            runTest {
                // Arrange
                val transactions =
                    (1..5).map { i ->
                        val txn =
                            Transaction(
                                id = i,
                                description = "Txn $i",
                                amount = i * 10.0,
                                date = 0L,
                                accountId = 1,
                                categoryId = 1,
                                notes = null,
                                source = "Manual Entry",
                            )
                        TransactionDetails(txn, emptyList(), "Cash", "Food", "food", "green", null)
                    }
                whenever(transactionRepository.getRecentManualTransactions(50)).thenReturn(flowOf(transactions))
                initializeViewModel()

                // Act & Assert
                viewModel.historyManualTransactions.test {
                    val result = awaitItem()
                    assertEquals(5, result.size)
                    cancelAndIgnoreRemainingEvents()
                }
            }

}

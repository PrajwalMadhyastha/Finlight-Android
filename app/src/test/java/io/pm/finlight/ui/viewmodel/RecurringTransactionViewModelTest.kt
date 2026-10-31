// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/RecurringTransactionViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// FIX (Testing) - Replaced ArgumentCaptor.capture() with the null-safe
// capture() helper to resolve a "capture() must not be null"
// NullPointerException when verifying suspend functions.
// FIX (Testing) - Added manual WorkManager initialization to resolve the
// "WorkManager is not initialized properly" exception.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.domain.usecase.ScheduleRecurringTransactionWorkerUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RecurringTransactionViewModelTest : BaseViewModelTest() {
    @Mock
    private lateinit var recurringTransactionRepository: IRecurringTransactionRepository

    @Mock
    private lateinit var scheduleWorkerUseCase: ScheduleRecurringTransactionWorkerUseCase

    @Captor
    private lateinit var recurringTransactionCaptor: ArgumentCaptor<RecurringTransaction>

    private lateinit var viewModel: RecurringTransactionViewModel

    @Before
    override fun setup() {
        super.setup()
    }

    private fun initializeViewModel(initialRules: List<RecurringTransaction> = emptyList()) {
        `when`(recurringTransactionRepository.getAll()).thenReturn(flowOf(initialRules))
        `when`(recurringTransactionRepository.getUnacknowledgedPatterns()).thenReturn(flowOf(emptyList()))
        viewModel = RecurringTransactionViewModel(recurringTransactionRepository, scheduleWorkerUseCase)
    }

    @Test
    fun `allRecurringTransactions flow emits rules from repository`() =
        runTest {
            // Arrange
            val rules =
                listOf(
                    RecurringTransaction(1, "Netflix", 149.0, TransactionType.EXPENSE, "Monthly", 0L, 1, 1, null),
                )
            initializeViewModel(rules)

            // Assert
            viewModel.allRecurringTransactions.test {
                assertEquals(rules, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `patternSuggestions flow emits suggestions from repository`() =
        runTest {
            // Arrange
            val pattern = RecurringPattern("NETFLIX", "Netflix", 149.0, TransactionType.EXPENSE, 1, 1, 2, System.currentTimeMillis(), System.currentTimeMillis(), false)
            `when`(recurringTransactionRepository.getAll()).thenReturn(flowOf(emptyList()))
            `when`(recurringTransactionRepository.getUnacknowledgedPatterns()).thenReturn(flowOf(listOf(pattern)))
            viewModel = RecurringTransactionViewModel(recurringTransactionRepository, scheduleWorkerUseCase)

            // Assert
            viewModel.patternSuggestions.test {
                assertEquals(listOf(pattern), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRuleById calls repository getById`() =
        runTest {
            // Arrange
            val ruleId = 1
            val rule = RecurringTransaction(ruleId, "Netflix", 149.0, TransactionType.EXPENSE, "Monthly", 0L, 1, 1, null)
            `when`(recurringTransactionRepository.getById(ruleId)).thenReturn(flowOf(rule))
            initializeViewModel()

            // Act
            viewModel.getRuleById(ruleId)

            // Assert
            verify(recurringTransactionRepository).getById(ruleId)
        }

    @Test
    fun `saveRule with null id calls repository insert and schedules worker`() =
        runTest {
            // Arrange
            initializeViewModel()

            // Act
            viewModel.saveRule(null, "Spotify", 129.0, TransactionType.EXPENSE, "Monthly", 0L, 1, 1, null, true, true, 1000L, "SPOTIFY", 5)
            advanceUntilIdle()

            // Assert
            verify(recurringTransactionRepository).insert(capture(recurringTransactionCaptor))
            verify(scheduleWorkerUseCase).invoke()
            val capturedRule = recurringTransactionCaptor.value
            assertEquals("Spotify", capturedRule.description)
            assertEquals(129.0, capturedRule.amount, 0.0)
            assertEquals(0, capturedRule.id) // ID is 0 for new entities
            assertEquals("SPOTIFY", capturedRule.smsSenderId)
            assertEquals(true, capturedRule.isVariableBill)
            assertEquals(true, capturedRule.autoApprove)
            assertEquals(1000L, capturedRule.endDate)
            assertEquals(5, capturedRule.skipCount)
        }

    @Test
    fun `saveRule with default parameters uses expected defaults and schedules worker`() =
        runTest {
            // Arrange
            initializeViewModel()

            // Act
            viewModel.saveRule(
                ruleId = null,
                description = "Gym Membership",
                amount = 45.0,
                recurrenceInterval = "Monthly",
                startDate = 1000L,
                accountId = 1,
                categoryId = 2,
                lastRunDate = null,
            )
            advanceUntilIdle()

            // Assert
            verify(recurringTransactionRepository).insert(capture(recurringTransactionCaptor))
            verify(scheduleWorkerUseCase).invoke()
            val capturedRule = recurringTransactionCaptor.value
            assertEquals(0, capturedRule.id)
            assertEquals("Gym Membership", capturedRule.description)
            assertEquals(45.0, capturedRule.amount, 0.0)
            assertEquals(TransactionType.EXPENSE, capturedRule.transactionType)
            assertEquals("Monthly", capturedRule.recurrenceInterval)
            assertEquals(1000L, capturedRule.startDate)
            assertEquals(1, capturedRule.accountId)
            assertEquals(2, capturedRule.categoryId)
            assertEquals(null, capturedRule.lastRunDate)
            assertEquals(false, capturedRule.isVariableBill)
            assertEquals(false, capturedRule.autoApprove)
            assertEquals(null, capturedRule.endDate)
            assertEquals(null, capturedRule.smsSenderId)
            assertEquals(0, capturedRule.skipCount)
        }

    @Test
    fun `saveRule with existing id calls repository update without scheduling worker`() =
        runTest {
            // Arrange
            initializeViewModel()
            val ruleId = 10

            // Act
            viewModel.saveRule(ruleId, "Updated Spotify", 199.0, TransactionType.EXPENSE, "Monthly", 0L, 1, 1, null, false, false, null, null)
            advanceUntilIdle()

            // Assert
            verify(recurringTransactionRepository).update(capture(recurringTransactionCaptor))
            verify(scheduleWorkerUseCase, Mockito.never()).invoke()
            val capturedRule = recurringTransactionCaptor.value
            assertEquals("Updated Spotify", capturedRule.description)
            assertEquals(199.0, capturedRule.amount, 0.0)
            assertEquals(ruleId, capturedRule.id)
            assertEquals(false, capturedRule.isVariableBill)
        }

    @Test
    fun `deleteRule calls repository delete`() =
        runTest {
            // Arrange
            initializeViewModel()
            val ruleToDelete = RecurringTransaction(1, "Old Subscription", 99.0, TransactionType.EXPENSE, "Monthly", 0L, 1, 1, null)

            // Act
            viewModel.deleteRule(ruleToDelete)
            advanceUntilIdle()

            // Assert
            verify(recurringTransactionRepository).delete(ruleToDelete)
        }

    @Test
    fun `dismissPatternSuggestion calls repository dismissPatternSuggestion`() =
        runTest {
            // Arrange
            val pattern = RecurringPattern("NETFLIX", "Netflix", 149.0, TransactionType.EXPENSE, 1, 1, 2, System.currentTimeMillis(), System.currentTimeMillis(), false)
            initializeViewModel()

            // Act
            viewModel.dismissPatternSuggestion(pattern)
            advanceUntilIdle()

            // Assert
            verify(recurringTransactionRepository).dismissPatternSuggestion(pattern)
        }
}

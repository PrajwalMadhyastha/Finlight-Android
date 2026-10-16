package io.pm.finlight.ui.viewmodel

import app.cash.turbine.test
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class PendingTransactionsViewModelTest : BaseViewModelTest() {
    private lateinit var viewModel: PendingTransactionsViewModel
    private lateinit var transactionRepository: ITransactionRepository
    private lateinit var recurringTransactionRepository: IRecurringTransactionRepository
    private lateinit var testDispatcherProvider: TestDispatcherProvider

    @Before
    override fun setup() {
        super.setup()
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)

        transactionRepository = mockk(relaxed = true)
        recurringTransactionRepository = mockk(relaxed = true)

        every { transactionRepository.getPendingTransactionsFlow() } returns MutableStateFlow(emptyList())

        viewModel =
            PendingTransactionsViewModel(
                transactionRepository = transactionRepository,
                recurringTransactionRepository = recurringTransactionRepository,
                dispatcherProvider = testDispatcherProvider,
            )
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkAll()
    }

    @Test
    fun `pendingTransactions emits from repository flow`() =
        runTest {
            // Arrange
            val pendingTxn =
                Transaction(
                    id = 1,
                    description = "Test Pending",
                    amount = 100.0,
                    transactionType = TransactionType.EXPENSE,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    status = TransactionStatus.PENDING,
                )
            val flow = MutableStateFlow(listOf(pendingTxn))
            every { transactionRepository.getPendingTransactionsFlow() } returns flow

            // Act
            val customViewModel =
                PendingTransactionsViewModel(
                    transactionRepository = transactionRepository,
                    recurringTransactionRepository = recurringTransactionRepository,
                    dispatcherProvider = testDispatcherProvider,
                )

            // Assert
            customViewModel.pendingTransactions.test {
                assertEquals(listOf(pendingTxn), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `confirmPending confirms transaction and resets skip count on recurring rule`() =
        runTest {
            // Act
            val testTimestamp = 1_700_000_000_000L
            viewModel.confirmPending(
                draftId = 1,
                ruleId = 2,
                confirmedAmount = null,
                lastRunDate = testTimestamp,
            )
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.confirmTransaction(1, null) }
            coVerify(exactly = 1) {
                recurringTransactionRepository.updateLastRunAndSkipCount(
                    ruleId = 2,
                    lastRunDate = testTimestamp,
                    skipCount = 0,
                )
            }
        }

    @Test
    fun `confirmPending with custom amount confirms atomically via repository`() =
        runTest {
            // Act
            val testTimestamp = 1_700_000_000_000L
            viewModel.confirmPending(
                draftId = 1,
                ruleId = 2,
                confirmedAmount = 150.0,
                lastRunDate = testTimestamp,
            )
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.confirmTransaction(1, 150.0) }
            coVerify(exactly = 1) {
                recurringTransactionRepository.updateLastRunAndSkipCount(
                    ruleId = 2,
                    lastRunDate = testTimestamp,
                    skipCount = 0,
                )
            }
        }

    @Test
    fun `confirmPending with default timestamp verifies execution`() =
        runTest {
            // Act
            viewModel.confirmPending(draftId = 1, ruleId = 2, confirmedAmount = 100.0)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.confirmTransaction(1, 100.0) }
            coVerify(exactly = 1) {
                recurringTransactionRepository.updateLastRunAndSkipCount(
                    ruleId = 2,
                    lastRunDate = any(),
                    skipCount = 0,
                )
            }
        }

    @Test
    fun `confirmPending without ruleId confirms transaction and skips recurring rule update`() =
        runTest {
            // Act
            viewModel.confirmPending(draftId = 1, ruleId = null, confirmedAmount = 75.0)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.confirmTransaction(1, 75.0) }
            coVerify(exactly = 0) {
                recurringTransactionRepository.updateLastRunAndSkipCount(any(), any(), any())
            }
            coVerify(exactly = 0) {
                recurringTransactionRepository.updateLastRunDate(any(), any())
            }
        }

    @Test
    fun `skipPending skips transaction and increments skip count when rule exists`() =
        runTest {
            // Arrange
            val rule =
                RecurringTransaction(
                    id = 2,
                    description = "Test Rule",
                    amount = 100.0,
                    transactionType = TransactionType.EXPENSE,
                    recurrenceInterval = "Monthly",
                    startDate = 0L,
                    accountId = 1,
                    categoryId = 1,
                    skipCount = 1,
                )
            coEvery { recurringTransactionRepository.getRuleById(2) } returns rule

            // Act
            val testTimestamp = 1_700_000_000_000L
            viewModel.skipPending(draftId = 1, ruleId = 2, lastRunDate = testTimestamp)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.skipTransaction(1) }
            coVerify(exactly = 1) { recurringTransactionRepository.getRuleById(2) }
            coVerify(exactly = 1) {
                recurringTransactionRepository.updateLastRunAndSkipCount(
                    ruleId = 2,
                    lastRunDate = testTimestamp,
                    skipCount = 2,
                )
            }
            coVerify(exactly = 0) { recurringTransactionRepository.updateLastRunDate(any(), any()) }
        }

    @Test
    fun `skipPending skips transaction and ignores recurring update when rule does not exist`() =
        runTest {
            // Arrange
            coEvery { recurringTransactionRepository.getRuleById(2) } returns null

            // Act
            viewModel.skipPending(draftId = 1, ruleId = 2)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.skipTransaction(1) }
            coVerify(exactly = 1) { recurringTransactionRepository.getRuleById(2) }
            coVerify(exactly = 0) {
                recurringTransactionRepository.updateLastRunAndSkipCount(any(), any(), any())
            }
            coVerify(exactly = 0) { recurringTransactionRepository.updateLastRunDate(any(), any()) }
        }

    @Test
    fun `skipPending without ruleId skips transaction and bypasses recurring rule lookup`() =
        runTest {
            // Act
            viewModel.skipPending(draftId = 1, ruleId = null)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.skipTransaction(1) }
            coVerify(exactly = 0) { recurringTransactionRepository.getRuleById(any()) }
            coVerify(exactly = 0) {
                recurringTransactionRepository.updateLastRunAndSkipCount(any(), any(), any())
            }
            coVerify(exactly = 0) { recurringTransactionRepository.updateLastRunDate(any(), any()) }
        }

    @Test
    fun `skipPending with explicit skipCount and lastRunDate updates rule directly`() =
        runTest {
            // Act
            val customLastRun = 1_700_000_000_000L
            viewModel.skipPending(draftId = 10, ruleId = 20, lastRunDate = customLastRun, skipCount = 5)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.skipTransaction(10) }
            coVerify(exactly = 0) { recurringTransactionRepository.getRuleById(any()) }
            coVerify(exactly = 1) {
                recurringTransactionRepository.updateLastRunAndSkipCount(
                    ruleId = 20,
                    lastRunDate = customLastRun,
                    skipCount = 5,
                )
            }
        }

    @Test
    fun `confirmPending with negative amount aborts without invoking repository`() =
        runTest {
            // Act
            viewModel.confirmPending(draftId = 1, ruleId = 2, confirmedAmount = -10.0)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 0) { transactionRepository.confirmTransaction(any(), any()) }
            coVerify(exactly = 0) { recurringTransactionRepository.updateLastRunAndSkipCount(any(), any(), any()) }
        }

    @Test
    fun `confirmPending with zero amount aborts without invoking repository`() =
        runTest {
            // Act
            viewModel.confirmPending(draftId = 1, ruleId = 2, confirmedAmount = 0.0)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 0) { transactionRepository.confirmTransaction(any(), any()) }
            coVerify(exactly = 0) { recurringTransactionRepository.updateLastRunAndSkipCount(any(), any(), any()) }
        }

    @Test
    fun `confirmPending with NaN amount aborts without invoking repository`() =
        runTest {
            // Act
            viewModel.confirmPending(draftId = 1, ruleId = 2, confirmedAmount = Double.NaN)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 0) { transactionRepository.confirmTransaction(any(), any()) }
            coVerify(exactly = 0) { recurringTransactionRepository.updateLastRunAndSkipCount(any(), any(), any()) }
        }

    @Test
    fun `confirmPending with infinite amount aborts without invoking repository`() =
        runTest {
            // Act
            viewModel.confirmPending(draftId = 1, ruleId = 2, confirmedAmount = Double.POSITIVE_INFINITY)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 0) { transactionRepository.confirmTransaction(any(), any()) }
            coVerify(exactly = 0) { recurringTransactionRepository.updateLastRunAndSkipCount(any(), any(), any()) }
        }

    @Test
    fun `confirmPending handles repository exception gracefully without crashing coroutine`() =
        runTest {
            // Arrange
            coEvery { transactionRepository.confirmTransaction(1, null) } throws RuntimeException("Database error")

            // Act
            viewModel.confirmPending(draftId = 1, ruleId = 2)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.confirmTransaction(1, null) }
            coVerify(exactly = 0) { recurringTransactionRepository.updateLastRunAndSkipCount(any(), any(), any()) }
        }

    @Test
    fun `skipPending handles repository exception gracefully without crashing coroutine`() =
        runTest {
            // Arrange
            coEvery { transactionRepository.skipTransaction(1) } throws RuntimeException("Database error")

            // Act
            viewModel.skipPending(draftId = 1, ruleId = 2)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.skipTransaction(1) }
            coVerify(exactly = 0) { recurringTransactionRepository.updateLastRunAndSkipCount(any(), any(), any()) }
        }

    @Test
    fun `confirmPending executes recurring update even if job is cancelled during confirmTransaction`() =
        runTest {
            // Arrange
            val testTimestamp = 1_700_000_000_000L
            coEvery { transactionRepository.confirmTransaction(1, null) } coAnswers {
                viewModel.viewModelScope.cancel()
            }

            // Act
            viewModel.confirmPending(draftId = 1, ruleId = 2, lastRunDate = testTimestamp)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.confirmTransaction(1, null) }
            coVerify(exactly = 1) {
                recurringTransactionRepository.updateLastRunAndSkipCount(
                    ruleId = 2,
                    lastRunDate = testTimestamp,
                    skipCount = 0,
                )
            }
        }

    @Test
    fun `skipPending executes recurring update even if job is cancelled during skipTransaction`() =
        runTest {
            // Arrange
            val testTimestamp = 1_700_000_000_000L
            coEvery { recurringTransactionRepository.getRuleById(2) } returns
                RecurringTransaction(
                    id = 2,
                    description = "Test Rule",
                    amount = 100.0,
                    transactionType = TransactionType.EXPENSE,
                    recurrenceInterval = "Monthly",
                    startDate = 0L,
                    accountId = 1,
                    categoryId = 1,
                    skipCount = 0,
                )
            coEvery { transactionRepository.skipTransaction(1) } coAnswers {
                viewModel.viewModelScope.cancel()
            }

            // Act
            viewModel.skipPending(draftId = 1, ruleId = 2, lastRunDate = testTimestamp)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionRepository.skipTransaction(1) }
            coVerify(exactly = 1) {
                recurringTransactionRepository.updateLastRunAndSkipCount(
                    ruleId = 2,
                    lastRunDate = testTimestamp,
                    skipCount = 1,
                )
            }
        }

    @Test
    fun `confirmPending emits error to uiEvent on invalid amount`() =
        runTest {
            viewModel.uiEvent.test {
                viewModel.confirmPending(draftId = 1, ruleId = 2, confirmedAmount = -5.0)
                assertEquals("Invalid amount: must be greater than 0", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `confirmPending emits error to uiEvent on repository exception`() =
        runTest {
            coEvery { transactionRepository.confirmTransaction(1, null) } throws RuntimeException("Disk failure")

            viewModel.uiEvent.test {
                viewModel.confirmPending(draftId = 1, ruleId = 2)
                assertEquals("Failed to confirm transaction: Disk failure", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `skipPending emits error to uiEvent on repository exception`() =
        runTest {
            coEvery { transactionRepository.skipTransaction(1) } throws RuntimeException("DB locked")

            viewModel.uiEvent.test {
                viewModel.skipPending(draftId = 1, ruleId = 2)
                assertEquals("Failed to skip transaction: DB locked", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

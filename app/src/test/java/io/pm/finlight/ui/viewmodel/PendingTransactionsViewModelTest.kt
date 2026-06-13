package io.pm.finlight.ui.viewmodel

import android.app.Application
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.PendingTransactionsViewModel
import io.pm.finlight.Transaction
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.RecurringTransactionDao
import io.pm.finlight.TransactionDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class PendingTransactionsViewModelTest : BaseViewModelTest() {
    private lateinit var application: Application
    private lateinit var viewModel: PendingTransactionsViewModel
    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var recurringDao: RecurringTransactionDao

    @Before
    override fun setup() {
        super.setup()
        application = mockk(relaxed = true)

        db = mockk()
        transactionDao = mockk(relaxed = true)
        recurringDao = mockk(relaxed = true)

        every { db.transactionDao() } returns transactionDao
        every { db.recurringTransactionDao() } returns recurringDao

        AppDatabase.setTestInstance(db)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkAll()
    }

    @Test
    fun `pendingTransactions emits from dao`() =
        runTest {
            // Arrange
            val pendingTxn = Transaction(id = 1, description = "Test", amount = 100.0, transactionType = "expense", date = 0L, accountId = 1, categoryId = 1, notes = null, status = "PENDING")
            val flow = MutableStateFlow(listOf(pendingTxn))
            every { transactionDao.getPendingTransactions() } returns flow

            // Act
            viewModel = PendingTransactionsViewModel(application)

            // Assert
            viewModel.pendingTransactions.test {
                assertEquals(listOf(pendingTxn), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `confirmPending confirms transaction and updates rule`() =
        runTest {
            // Arrange
            every { transactionDao.getPendingTransactions() } returns MutableStateFlow(emptyList())
            viewModel = PendingTransactionsViewModel(application)

            // Act
            viewModel.confirmPending(draftId = 1, ruleId = 2, confirmedAmount = null)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 0) { transactionDao.updateAmount(any(), any()) }
            coVerify(exactly = 1) { transactionDao.confirmTransaction(1) }
            coVerify(exactly = 1) { recurringDao.updateLastRunDate(eq(2), any()) }
            coVerify(exactly = 1) { recurringDao.updateSkipCount(2, 0) }
        }

    @Test
    fun `confirmPending with custom amount updates amount before confirming`() =
        runTest {
            // Arrange
            every { transactionDao.getPendingTransactions() } returns MutableStateFlow(emptyList())
            viewModel = PendingTransactionsViewModel(application)

            // Act
            viewModel.confirmPending(draftId = 1, ruleId = 2, confirmedAmount = 150.0)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionDao.updateAmount(1, 150.0) }
            coVerify(exactly = 1) { transactionDao.confirmTransaction(1) }
            coVerify(exactly = 1) { recurringDao.updateLastRunDate(eq(2), any()) }
            coVerify(exactly = 1) { recurringDao.updateSkipCount(2, 0) }
        }

    @Test
    fun `skipPending skips transaction and increments skip count`() =
        runTest {
            // Arrange
            every { transactionDao.getPendingTransactions() } returns MutableStateFlow(emptyList())
            val rule = RecurringTransaction(id = 2, description = "Test", amount = 100.0, transactionType = "expense", recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = 1, skipCount = 1)
            coEvery { recurringDao.getAllRulesList() } returns listOf(rule)
            viewModel = PendingTransactionsViewModel(application)

            // Act
            viewModel.skipPending(draftId = 1, ruleId = 2)
            advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { transactionDao.skipTransaction(1) }
            coVerify(exactly = 1) { recurringDao.updateLastRunDate(eq(2), any()) }
            coVerify(exactly = 1) { recurringDao.updateSkipCount(2, 2) } // 1 + 1 = 2
        }
}

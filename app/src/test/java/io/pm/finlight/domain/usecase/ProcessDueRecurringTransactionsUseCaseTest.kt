package io.pm.finlight.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionStatus
import io.pm.finlight.TransactionType
import io.pm.finlight.data.TransactionRunner
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessDueRecurringTransactionsUseCaseTest {
    private val recurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)
    private val transactionRepo: ITransactionRepository = mockk(relaxed = true)
    private val fakeTransactionRunner = RecordingTransactionRunner()

    private lateinit var useCase: DefaultProcessDueRecurringTransactionsUseCase

    private class RecordingTransactionRunner : TransactionRunner {
        var executionCount = 0

        override suspend fun <R> run(block: suspend () -> R): R {
            executionCount++
            return block()
        }
    }

    @Before
    fun setUp() {
        fakeTransactionRunner.executionCount = 0
        useCase =
            DefaultProcessDueRecurringTransactionsUseCase(
                recurringTransactionRepository = recurringRepo,
                transactionRepository = transactionRepo,
                transactionRunner = fakeTransactionRunner,
                dispatcherProvider = TestDispatcherProvider(),
            )
    }

    private fun createRule(
        id: Int = 1,
        description: String = "Test Rule",
        amount: Double = 100.0,
        startDate: Long = 0L,
        endDate: Long? = null,
        lastRunDate: Long? = null,
        recurrenceInterval: String = "Monthly",
        autoApprove: Boolean = false,
    ) = RecurringTransaction(
        id = id,
        description = description,
        amount = amount,
        transactionType = TransactionType.EXPENSE,
        recurrenceInterval = recurrenceInterval,
        startDate = startDate,
        endDate = endDate,
        lastRunDate = lastRunDate,
        accountId = 1,
        categoryId = 2,
        autoApprove = autoApprove,
    )

    private fun buildCalendar(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Calendar =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun `normalizes dates to start-of-day so rule created after 3 AM is due today`() =
        runTest {
            val today1500 = buildCalendar(2026, Calendar.NOVEMBER, 6, 15, 0).timeInMillis
            val today0300 = buildCalendar(2026, Calendar.NOVEMBER, 6, 3, 0).timeInMillis

            val rule = createRule(id = 1, startDate = today1500, recurrenceInterval = "Daily")
            coEvery { recurringRepo.getAllRulesList() } returns listOf(rule)
            coEvery { transactionRepo.hasExecutedTransactionForRuleSince(1, any()) } returns false
            coEvery { transactionRepo.getPendingTransactionForRule(1) } returns null
            coEvery { transactionRepo.insertTransaction(any()) } returns 101L

            val results = useCase(today0300)

            assertEquals(1, results.size)
            assertTrue(results.first() is ProcessedRecurringResult.PendingDraftCreated)
            assertEquals(1, fakeTransactionRunner.executionCount)
        }

    @Test
    fun `skips future rules where start date is tomorrow`() =
        runTest {
            val today = buildCalendar(2026, Calendar.NOVEMBER, 6, 10, 0).timeInMillis
            val tomorrow = buildCalendar(2026, Calendar.NOVEMBER, 7, 9, 0).timeInMillis

            val rule = createRule(id = 2, startDate = tomorrow, recurrenceInterval = "Daily")
            coEvery { recurringRepo.getAllRulesList() } returns listOf(rule)

            val results = useCase(today)

            assertTrue(results.isEmpty())
            assertEquals(0, fakeTransactionRunner.executionCount)
        }

    @Test
    fun `skips expired rules whose end date has passed`() =
        runTest {
            val today = buildCalendar(2026, Calendar.NOVEMBER, 6, 10, 0).timeInMillis
            val yesterday = buildCalendar(2026, Calendar.NOVEMBER, 5, 10, 0).timeInMillis

            val rule = createRule(id = 3, startDate = 0L, endDate = yesterday, recurrenceInterval = "Daily")
            coEvery { recurringRepo.getAllRulesList() } returns listOf(rule)

            val results = useCase(today)

            assertTrue(results.isEmpty())
            assertEquals(0, fakeTransactionRunner.executionCount)
        }

    @Test
    fun `skips non-due rules when lastRunDate was today`() =
        runTest {
            val today10am = buildCalendar(2026, Calendar.NOVEMBER, 6, 10, 0).timeInMillis
            val today8am = buildCalendar(2026, Calendar.NOVEMBER, 6, 8, 0).timeInMillis

            val rule = createRule(id = 4, startDate = 0L, lastRunDate = today8am, recurrenceInterval = "Daily")
            coEvery { recurringRepo.getAllRulesList() } returns listOf(rule)

            val results = useCase(today10am)

            assertTrue(results.isEmpty())
            assertEquals(0, fakeTransactionRunner.executionCount)
        }

    @Test
    fun `autoApprove path inserts confirmed transaction and updates lastRunDate with cycle anchor`() =
        runTest {
            val now = buildCalendar(2026, Calendar.NOVEMBER, 6, 10, 0).timeInMillis
            val rule = createRule(id = 5, startDate = 0L, autoApprove = true, recurrenceInterval = "Daily")
            coEvery { recurringRepo.getAllRulesList() } returns listOf(rule)
            coEvery { transactionRepo.hasExecutedTransactionForRuleSince(5, any()) } returns false
            coEvery { transactionRepo.getPendingTransactionForRule(5) } returns null

            val txnSlot = slot<Transaction>()
            coEvery { transactionRepo.insertTransaction(capture(txnSlot)) } returns 200L

            val results = useCase(now)

            assertEquals(1, results.size)
            val autoApproved = results.first() as ProcessedRecurringResult.AutoApproved
            assertEquals(200L, autoApproved.transactionId)
            assertEquals(TransactionStatus.CONFIRMED, txnSlot.captured.status)

            val expectedCycleStart = buildCalendar(2026, Calendar.NOVEMBER, 6, 0, 0).timeInMillis
            assertEquals(expectedCycleStart, txnSlot.captured.date)
            coVerify(exactly = 1) { recurringRepo.updateLastRunDate(5, expectedCycleStart) }
        }

    @Test
    fun `standard path inserts pending draft and does not prematurely advance lastRunDate`() =
        runTest {
            val now = buildCalendar(2026, Calendar.NOVEMBER, 6, 10, 0).timeInMillis
            val rule = createRule(id = 6, startDate = 0L, autoApprove = false, recurrenceInterval = "Daily")
            coEvery { recurringRepo.getAllRulesList() } returns listOf(rule)
            coEvery { transactionRepo.hasExecutedTransactionForRuleSince(6, any()) } returns false
            coEvery { transactionRepo.getPendingTransactionForRule(6) } returns null

            val txnSlot = slot<Transaction>()
            coEvery { transactionRepo.insertTransaction(capture(txnSlot)) } returns 300L

            val results = useCase(now)

            assertEquals(1, results.size)
            val pendingResult = results.first() as ProcessedRecurringResult.PendingDraftCreated
            assertEquals(300L, pendingResult.draftTransactionId)
            assertEquals(TransactionStatus.PENDING, txnSlot.captured.status)

            val expectedCycleStart = buildCalendar(2026, Calendar.NOVEMBER, 6, 0, 0).timeInMillis
            assertEquals(expectedCycleStart, txnSlot.captured.date)
            coVerify(exactly = 0) { recurringRepo.updateLastRunDate(any(), any()) }
        }

    @Test
    fun `skips when transaction has already executed for current cycle`() =
        runTest {
            val now = buildCalendar(2026, Calendar.NOVEMBER, 6, 10, 0).timeInMillis
            val rule = createRule(id = 7, startDate = 0L, recurrenceInterval = "Daily")
            coEvery { recurringRepo.getAllRulesList() } returns listOf(rule)
            coEvery { transactionRepo.hasExecutedTransactionForRuleSince(7, any()) } returns true

            val results = useCase(now)

            assertEquals(1, results.size)
            val skipped = results.first() as ProcessedRecurringResult.Skipped
            assertEquals("Already executed transaction for current cycle", skipped.reason)
            coVerify(exactly = 0) { transactionRepo.insertTransaction(any()) }
        }

    @Test
    fun `skips when pending draft already exists for rule`() =
        runTest {
            val now = buildCalendar(2026, Calendar.NOVEMBER, 6, 10, 0).timeInMillis
            val rule = createRule(id = 8, description = "Electricity", startDate = 0L, recurrenceInterval = "Daily")
            val existingDraft =
                Transaction(
                    id = 88,
                    description = "Electricity",
                    amount = 100.0,
                    date = now,
                    accountId = 1,
                    categoryId = 2,
                    notes = null,
                    status = TransactionStatus.PENDING,
                )

            coEvery { recurringRepo.getAllRulesList() } returns listOf(rule)
            coEvery { transactionRepo.hasExecutedTransactionForRuleSince(8, any()) } returns false
            coEvery { transactionRepo.getPendingTransactionForRule(8) } returns existingDraft

            val results = useCase(now)

            assertEquals(1, results.size)
            val skipped = results.first() as ProcessedRecurringResult.Skipped
            assertTrue(skipped.reason.contains("already has a pending draft"))
            coVerify(exactly = 0) { transactionRepo.insertTransaction(any()) }
        }

    @Test
    fun `calculateTargetCycleStartEpochMs handles daily interval`() {
        val now = buildCalendar(2026, Calendar.NOVEMBER, 6, 14, 30).timeInMillis
        val rule = createRule(recurrenceInterval = "Daily")

        val cycleStart = calculateTargetCycleStartEpochMs(rule, now)
        val expected = buildCalendar(2026, Calendar.NOVEMBER, 6, 0, 0).timeInMillis
        assertEquals(expected, cycleStart)
    }

    @Test
    fun `calculateTargetCycleStartEpochMs handles weekly interval`() {
        // Monday: Nov 2, 2026
        val mondayStart = buildCalendar(2026, Calendar.NOVEMBER, 2, 10, 0).timeInMillis
        // Thursday: Nov 5, 2026
        val thursdayNow = buildCalendar(2026, Calendar.NOVEMBER, 5, 14, 0).timeInMillis

        val rule = createRule(startDate = mondayStart, recurrenceInterval = "Weekly")
        val cycleStart = calculateTargetCycleStartEpochMs(rule, thursdayNow)

        val expected = buildCalendar(2026, Calendar.NOVEMBER, 2, 0, 0).timeInMillis
        assertEquals(expected, cycleStart)
    }

    @Test
    fun `calculateTargetCycleStartEpochMs neutralizes 31-day rollover in monthly calculation`() {
        // Rule on 31st of month (e.g. Jan 31)
        val ruleStart = buildCalendar(2025, Calendar.JANUARY, 31, 10, 0).timeInMillis
        // Now is March 2nd, 2025 (target cycle anchor should be Feb 28, 2025 since March 31 > March 2)
        val march2 = buildCalendar(2025, Calendar.MARCH, 2, 12, 0).timeInMillis

        val rule = createRule(startDate = ruleStart, recurrenceInterval = "Monthly")
        val cycleStart = calculateTargetCycleStartEpochMs(rule, march2)

        val expected = buildCalendar(2025, Calendar.FEBRUARY, 28, 0, 0).timeInMillis
        assertEquals(expected, cycleStart)
    }

    @Test
    fun `calculateTargetCycleStartEpochMs steps back year in yearly calculation when now is earlier in year`() {
        val ruleStart = buildCalendar(2024, Calendar.JULY, 1, 10, 0).timeInMillis
        val march2025 = buildCalendar(2025, Calendar.MARCH, 15, 12, 0).timeInMillis

        val rule = createRule(startDate = ruleStart, recurrenceInterval = "Yearly")
        val cycleStart = calculateTargetCycleStartEpochMs(rule, march2025)

        val expected = buildCalendar(2024, Calendar.JULY, 1, 0, 0).timeInMillis
        assertEquals(expected, cycleStart)
    }

    @Test
    fun `isDue returns false when today is before rule start date`() {
        val today = buildCalendar(2026, Calendar.NOVEMBER, 6, 10, 0).timeInMillis
        val tomorrow = buildCalendar(2026, Calendar.NOVEMBER, 7, 10, 0).timeInMillis
        val rule = createRule(startDate = tomorrow, recurrenceInterval = "Daily")

        val result = isDue(rule, today)
        assertEquals(false, result)
    }

    @Test
    fun `isDue evaluates weekly monthly and yearly intervals correctly`() {
        val today = buildCalendar(2026, Calendar.NOVEMBER, 6, 0, 0).timeInMillis

        val weeklyDueLastRun = buildCalendar(2026, Calendar.OCTOBER, 29, 0, 0).timeInMillis
        val weeklyNotDueLastRun = buildCalendar(2026, Calendar.NOVEMBER, 2, 0, 0).timeInMillis
        val weeklyRuleDue = createRule(startDate = 0L, lastRunDate = weeklyDueLastRun, recurrenceInterval = "Weekly")
        val weeklyRuleNotDue = createRule(startDate = 0L, lastRunDate = weeklyNotDueLastRun, recurrenceInterval = "Weekly")
        assertEquals(true, isDue(weeklyRuleDue, today))
        assertEquals(false, isDue(weeklyRuleNotDue, today))

        val monthlyDueLastRun = buildCalendar(2026, Calendar.OCTOBER, 5, 0, 0).timeInMillis
        val monthlyNotDueLastRun = buildCalendar(2026, Calendar.OCTOBER, 15, 0, 0).timeInMillis
        val monthlyRuleDue = createRule(startDate = 0L, lastRunDate = monthlyDueLastRun, recurrenceInterval = "Monthly")
        val monthlyRuleNotDue = createRule(startDate = 0L, lastRunDate = monthlyNotDueLastRun, recurrenceInterval = "Monthly")
        assertEquals(true, isDue(monthlyRuleDue, today))
        assertEquals(false, isDue(monthlyRuleNotDue, today))

        val yearlyDueLastRun = buildCalendar(2025, Calendar.NOVEMBER, 5, 0, 0).timeInMillis
        val yearlyNotDueLastRun = buildCalendar(2026, Calendar.JUNE, 1, 0, 0).timeInMillis
        val yearlyRuleDue = createRule(startDate = 0L, lastRunDate = yearlyDueLastRun, recurrenceInterval = "Yearly")
        val yearlyRuleNotDue = createRule(startDate = 0L, lastRunDate = yearlyNotDueLastRun, recurrenceInterval = "Yearly")
        assertEquals(true, isDue(yearlyRuleDue, today))
        assertEquals(false, isDue(yearlyRuleNotDue, today))
    }

    @Test
    fun `invoke with default parameter evaluates rules using current system time`() =
        runTest {
            coEvery { recurringRepo.getAllRulesList() } returns emptyList()
            val results = useCase()
            assertTrue(results.isEmpty())
        }
}

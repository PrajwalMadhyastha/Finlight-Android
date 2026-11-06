package io.pm.finlight.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.RecurringPattern
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionType
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyzeRecurringPatternsUseCaseTest {
    private val transactionRepo: ITransactionRepository = mockk(relaxed = true)
    private val recurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)

    private lateinit var useCase: DefaultAnalyzeRecurringPatternsUseCase

    @Before
    fun setUp() {
        useCase =
            DefaultAnalyzeRecurringPatternsUseCase(
                transactionRepository = transactionRepo,
                recurringTransactionRepository = recurringRepo,
                dispatcherProvider = TestDispatcherProvider(),
            )
    }

    private fun createTransaction(
        id: Int,
        signature: String?,
        date: Long,
        description: String = "Spotify",
        amount: Double = 119.0,
    ) = Transaction(
        id = id,
        smsSignature = signature,
        date = date,
        description = description,
        amount = amount,
        transactionType = TransactionType.EXPENSE,
        accountId = 1,
        categoryId = 2,
        notes = null,
    )

    private fun createPattern(
        signature: String,
        description: String = "Spotify",
        occurrences: Int = 1,
        firstSeen: Long = 0L,
        lastSeen: Long = 0L,
    ) = RecurringPattern(
        smsSignature = signature,
        description = description,
        amount = 119.0,
        transactionType = TransactionType.EXPENSE,
        accountId = 1,
        categoryId = 2,
        occurrences = occurrences,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
    )

    @Test
    fun `inserts new pattern when signed transaction is processed for first time`() =
        runTest {
            val now = 1000000000L
            val txn = createTransaction(id = 1, signature = "spotify_sig", date = now)

            coEvery { transactionRepo.getTransactionsWithSignatureSince(any()) } returns listOf(txn)
            coEvery { recurringRepo.getPatternBySignature("spotify_sig") } returns null
            coEvery { recurringRepo.getAllPatterns() } returns emptyList()

            val patternSlot = slot<RecurringPattern>()
            coEvery { recurringRepo.insertPattern(capture(patternSlot)) } returns Unit

            val detected = useCase(now)

            assertTrue(detected.isEmpty())
            coVerify(exactly = 1) { recurringRepo.insertPattern(any()) }
            assertEquals("spotify_sig", patternSlot.captured.smsSignature)
            assertEquals(1, patternSlot.captured.occurrences)
            assertEquals(now, patternSlot.captured.firstSeen)
            assertEquals(now, patternSlot.captured.lastSeen)
        }

    @Test
    fun `updates existing pattern occurrence count and lastSeen`() =
        runTest {
            val now = 2000000000L
            val txn = createTransaction(id = 2, signature = "existing_sig", date = now)
            val existing = createPattern(signature = "existing_sig", occurrences = 1, firstSeen = 1000L, lastSeen = 1000L)

            coEvery { transactionRepo.getTransactionsWithSignatureSince(any()) } returns listOf(txn)
            coEvery { recurringRepo.getPatternBySignature("existing_sig") } returns existing
            coEvery { recurringRepo.getAllPatterns() } returns emptyList()

            val patternSlot = slot<RecurringPattern>()
            coEvery { recurringRepo.updatePattern(capture(patternSlot)) } returns Unit

            val detected = useCase(now)

            assertTrue(detected.isEmpty())
            coVerify(exactly = 1) { recurringRepo.updatePattern(any()) }
            assertEquals(2, patternSlot.captured.occurrences)
            assertEquals(now, patternSlot.captured.lastSeen)
        }

    @Test
    fun `ignores transactions without SMS signature`() =
        runTest {
            val now = 1000000000L
            val unsignedTxn = createTransaction(id = 3, signature = null, date = now)

            coEvery { transactionRepo.getTransactionsWithSignatureSince(any()) } returns listOf(unsignedTxn)
            coEvery { recurringRepo.getAllPatterns() } returns emptyList()

            val detected = useCase(now)

            assertTrue(detected.isEmpty())
            coVerify(exactly = 0) { recurringRepo.insertPattern(any()) }
            coVerify(exactly = 0) { recurringRepo.updatePattern(any()) }
        }

    @Test
    fun `detects weekly pattern meeting occurrence threshold and tolerance`() =
        runTest {
            val now = 3000000000L
            val sig = "weekly_sig"
            val pattern = createPattern(signature = sig, occurrences = 3)

            val t1 = now - TimeUnit.DAYS.toMillis(14)
            val t2 = now - TimeUnit.DAYS.toMillis(7)
            val t3 = now

            val txns =
                listOf(
                    createTransaction(1, sig, t1),
                    createTransaction(2, sig, t2),
                    createTransaction(3, sig, t3),
                )

            coEvery { transactionRepo.getTransactionsWithSignatureSince(any()) } returns emptyList()
            coEvery { recurringRepo.getAllPatterns() } returns listOf(pattern)
            coEvery { transactionRepo.getTransactionsBySignature(sig) } returns txns

            val detected = useCase(now)

            assertEquals(1, detected.size)
            assertEquals(sig, detected.first().smsSignature)
        }

    @Test
    fun `detects monthly pattern meeting occurrence threshold and tolerance`() =
        runTest {
            val now = 5000000000L
            val sig = "monthly_sig"
            val pattern = createPattern(signature = sig, occurrences = 3)

            val t1 = now - TimeUnit.DAYS.toMillis(60)
            val t2 = now - TimeUnit.DAYS.toMillis(30)
            val t3 = now

            val txns =
                listOf(
                    createTransaction(1, sig, t1),
                    createTransaction(2, sig, t2),
                    createTransaction(3, sig, t3),
                )

            coEvery { transactionRepo.getTransactionsWithSignatureSince(any()) } returns emptyList()
            coEvery { recurringRepo.getAllPatterns() } returns listOf(pattern)
            coEvery { transactionRepo.getTransactionsBySignature(sig) } returns txns

            val detected = useCase(now)

            assertEquals(1, detected.size)
            assertEquals(sig, detected.first().smsSignature)
        }

    @Test
    fun `ignores patterns with fewer than minimum occurrences`() =
        runTest {
            val now = 5000000000L
            val sig = "low_count_sig"
            val pattern = createPattern(signature = sig, occurrences = 2)

            coEvery { transactionRepo.getTransactionsWithSignatureSince(any()) } returns emptyList()
            coEvery { recurringRepo.getAllPatterns() } returns listOf(pattern)

            val detected = useCase(now)

            assertTrue(detected.isEmpty())
            coVerify(exactly = 0) { transactionRepo.getTransactionsBySignature(sig) }
        }

    @Test
    fun `ignores patterns with erratic intervals`() =
        runTest {
            val now = 5000000000L
            val sig = "erratic_sig"
            val pattern = createPattern(signature = sig, occurrences = 3)

            val t1 = now - TimeUnit.DAYS.toMillis(80)
            val t2 = now - TimeUnit.DAYS.toMillis(45)
            val t3 = now

            val txns =
                listOf(
                    createTransaction(1, sig, t1),
                    createTransaction(2, sig, t2),
                    createTransaction(3, sig, t3),
                )

            coEvery { transactionRepo.getTransactionsWithSignatureSince(any()) } returns emptyList()
            coEvery { recurringRepo.getAllPatterns() } returns listOf(pattern)
            coEvery { transactionRepo.getTransactionsBySignature(sig) } returns txns

            val detected = useCase(now)

            assertTrue(detected.isEmpty())
        }

    @Test
    fun `currentTimeMillis parameter queries transactions within 90 day window`() =
        runTest {
            val customNow = 1000000000000L
            val expectedSince = customNow - TimeUnit.DAYS.toMillis(90L)

            coEvery { transactionRepo.getTransactionsWithSignatureSince(expectedSince) } returns emptyList()
            coEvery { recurringRepo.getAllPatterns() } returns emptyList()

            val detected = useCase(customNow)

            assertTrue(detected.isEmpty())
            coVerify(exactly = 1) { transactionRepo.getTransactionsWithSignatureSince(expectedSince) }
        }

    @Test
    fun `invoke with default parameter evaluates patterns using current system time`() =
        runTest {
            coEvery { transactionRepo.getTransactionsWithSignatureSince(any()) } returns emptyList()
            coEvery { recurringRepo.getAllPatterns() } returns emptyList()

            val detected = useCase()

            assertTrue(detected.isEmpty())
        }
}

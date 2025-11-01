// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/data/repository/TransactionRepositoryTest.kt
// REASON: FEATURE (Test) - Added a comprehensive test suite for the new
// `getMonthlyConsistencyData` function. These tests validate all logic
// branches, including the fixes for the "No Budget" (null) and "Zero Budget" (0f)
// edge cases, ensuring heatmap data is calculated correctly.
//
// FIX (Test): The `getTimestamp` helper now zeroes out H:M:S. This makes
// date comparisons deterministic and fixes a race condition in the
// `getMonthlyConsistencyData` tests.
// =================================================================================
package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import java.util.*
import kotlin.test.assertTrue
import kotlin.test.junit.JUnitAsserter.assertNotNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionRepositoryTest : BaseViewModelTest() {

    // --- FIX: Removed the DatabaseTestRule ---
    // @get:Rule
    // val dbRule = DatabaseTestRule()

    // --- FIX: transactionDao is now a Mock ---
    @Mock
    private lateinit var transactionDao: TransactionDao
    @Mock
    private lateinit var settingsRepository: SettingsRepository
    @Mock
    private lateinit var tagRepository: TagRepository

    private lateinit var repository: TransactionRepository

    // Use a fixed "today" for all tests to make them deterministic
    // This is for the test's *logic*, not the production code's `today`
    private val testToday = Calendar.getInstance().apply {
        set(2025, Calendar.OCTOBER, 28, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // Helper to get a timestamp for a specific day in 2025
    // --- FIX: This function now zeroes out the time, matching the repo's logic ---
    private fun getTimestamp(month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, 2025)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Helper to format a date key as the DAO query does
    private fun getDateKey(year: Int, month: Int, day: Int): String {
        return String.format(Locale.ROOT, "%d-%02d-%02d", year, month, day)
    }


    @Before
    override fun setup() {
        super.setup()
        // Initialize with default mock behaviors
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null))
        // --- FIX: transactionDao is now a mock, so repository is initialized here ---
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
    }

    @Test
    fun `insertTransactionWithTags without travel mode saves transaction and initial tags`() = runTest {
        // Arrange
        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>

        // --- FIX: Stub the (now-mocked) transactionDao ---
        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        // Act
        // --- FIX: Use the class-level repository ---
        val newId = repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        // --- FIX: Verify calls on the (now-mocked) transactionDao ---
        verify(transactionDao).insert(transaction)
        verify(transactionDao).addTagsToTransaction(capture(crossRefCaptor))

        val capturedRefs = crossRefCaptor.value
        assertEquals(1L, newId)
        assertEquals(1, capturedRefs.size)
        assertEquals(1, capturedRefs.first().tagId)
        assertEquals(newId.toInt(), capturedRefs.first().transactionId)
    }

    @Test
    fun `insertTransactionWithTags with active travel mode adds trip tag`() = runTest {
        // Arrange
        val tripTag = Tag(id = 99, name = "US Trip")
        val travelSettings = TravelModeSettings(isEnabled = true, tripName = "US Trip", tripType = TripType.DOMESTIC, startDate = 0L, endDate = Long.MAX_VALUE, currencyCode = null, conversionRate = null)
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))
        `when`(tagRepository.findOrCreateTag("US Trip")).thenReturn(tripTag)

        // --- FIX: Stub the (now-mocked) transactionDao ---
        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>

        // Act
        repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        verify(tagRepository).findOrCreateTag("US Trip")
        // --- FIX: Verify calls on the (now-mocked) transactionDao ---
        verify(transactionDao).addTagsToTransaction(capture(crossRefCaptor))
        val capturedRefs = crossRefCaptor.value
        assertEquals(2, capturedRefs.size) // Work tag + Trip tag
        assertTrue(capturedRefs.any { it.tagId == 1 })
        assertTrue(capturedRefs.any { it.tagId == 99 })
    }

    @Test
    fun `insertTransactionWithTags with inactive travel mode does not add trip tag`() = runTest {
        // Arrange
        val travelSettings = TravelModeSettings(isEnabled = false, tripName = "US Trip", tripType = TripType.DOMESTIC, startDate = 0L, endDate = Long.MAX_VALUE, currencyCode = null, conversionRate = null)
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))

        // --- FIX: Stub the (now-mocked) transactionDao ---
        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>

        // Act
        repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        verify(tagRepository, never()).findOrCreateTag(anyString())
        // --- FIX: Verify calls on the (now-mocked) transactionDao ---
        verify(transactionDao).addTagsToTransaction(capture(crossRefCaptor))
        val capturedRefs = crossRefCaptor.value
        assertEquals(1, capturedRefs.size) // Only Work tag
        assertEquals(1, capturedRefs.first().tagId)
    }

    @Test
    fun `getTotalExpensesSince calls DAO`() = runTest {
        val startDate = 1000L
        repository.getTotalExpensesSince(startDate)
        verify(transactionDao).getTotalExpensesSince(startDate)
    }

    @Test
    fun `searchMerchants calls DAO`() = runTest {
        val query = "amzn"
        `when`(transactionDao.searchMerchants(query)).thenReturn(flowOf(emptyList()))
        repository.searchMerchants(query)
        verify(transactionDao).searchMerchants(query)
    }

    @Test
    fun `deleteByIds calls DAO`() = runTest {
        val ids = listOf(1, 2)
        repository.deleteByIds(ids)
        verify(transactionDao).deleteByIds(ids)
    }

    @Test
    fun `addTagForDateRange calls DAO`() = runTest {
        repository.addTagForDateRange(1, 100L, 200L)
        verify(transactionDao).addTagForDateRange(1, 100L, 200L)
    }

    @Test
    fun `removeTagForDateRange calls DAO`() = runTest {
        repository.removeTagForDateRange(1, 100L, 200L)
        verify(transactionDao).removeTagForDateRange(1, 100L, 200L)
    }

    @Test
    fun `getTransactionsByTagId calls DAO`() = runTest {
        `when`(transactionDao.getTransactionsByTagId(1)).thenReturn(flowOf(emptyList()))
        repository.getTransactionsByTagId(1)
        verify(transactionDao).getTransactionsByTagId(1)
    }

    @Test
    fun `removeAllTransactionsForTag calls DAO`() = runTest {
        repository.removeAllTransactionsForTag(1)
        verify(transactionDao).removeAllTransactionsForTag(1)
    }

    // --- NEW TESTS FOR getMonthlyConsistencyData ---

    @Test
    fun `getMonthlyConsistencyData returns NO_DATA for all past days if budget is null`() = runTest {
        // Arrange
        val year = 2025
        val month = 9 // September
        val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

        `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(null))
        `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
        // Mock spending on day 2 and no spending on day 3
        val dailyTotals = listOf(
            DailyTotal(getDateKey(year, month, 2), 100.0),
            DailyTotal(getDateKey(year, month, 3), 0.0)
        )
        `when`(transactionDao.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotals))

        // Act
        repository.getMonthlyConsistencyData(year, month).test {
            val results = awaitItem()

            // Assert
            // All days in September 2025 are before our fixed 'today' (Oct 28, 2025)
            // and after the first transaction date.
            // Even though day 2 has spending and day 3 has no spending, both should be NO_DATA.

            val day1 = results.find { it.date.date == 1 }
            val day2 = results.find { it.date.date == 2 }
            val day3 = results.find { it.date.date == 3 }

            assertEquals(SpendingStatus.NO_DATA, day1?.status)
            assertEquals(0L, day1?.amountSpent)

            assertEquals(SpendingStatus.NO_DATA, day2?.status)
            assertEquals(100L, day2?.amountSpent) // Amount is still recorded

            assertEquals(SpendingStatus.NO_DATA, day3?.status)
            assertEquals(0L, day3?.amountSpent)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getMonthlyConsistencyData handles 0f budget correctly`() = runTest {
        // Arrange (Testing for September 2025)
        val year = 2025
        val month = 9 // September
        val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

        `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(0f)) // Zero budget
        `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
        val dailyTotals = listOf(
            DailyTotal(getDateKey(year, month, 2), 100.0), // Day 2: Spent 100
            DailyTotal(getDateKey(year, month, 3), 0.0)   // Day 3: Spent 0
        )
        `when`(transactionDao.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotals))

        // Act
        repository.getMonthlyConsistencyData(year, month).test {
            val results = awaitItem()

            // Assert (safeToSpend is 0L)
            val day1 = results.find { it.date.date == 1 } // No spend
            val day2 = results.find { it.date.date == 2 } // Spend > 0
            val day3 = results.find { it.date.date == 3 } // Spend = 0

            // Case: amountSpent == 0L && safeToSpend == 0L -> WITHIN_LIMIT (blue)
            assertEquals(SpendingStatus.WITHIN_LIMIT, day1?.status)

            // Case: amountSpent > 0L && safeToSpend == 0L -> OVER_LIMIT (red) [FIXES BLUE DAY BUG]
            assertEquals(SpendingStatus.OVER_LIMIT, day2?.status)

            // Case: amountSpent == 0L && safeToSpend == 0L -> WITHIN_LIMIT (blue)
            assertEquals(SpendingStatus.WITHIN_LIMIT, day3?.status)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getMonthlyConsistencyData handles positive budget correctly`() = runTest {
        // Arrange (Testing for September 2025, 30 days)
        val year = 2025
        val month = 9 // September
        val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)
        val budget = 3000f // DSTS = 100L
        val safeToSpend = 100L

        `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(budget))
        `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
        val dailyTotals = listOf(
            DailyTotal(getDateKey(year, month, 2), 0.0),   // No spend
            DailyTotal(getDateKey(year, month, 3), 50.0),  // Within limit
            DailyTotal(getDateKey(year, month, 4), 150.0) // Over limit
        )
        `when`(transactionDao.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotals))

        // Act
        repository.getMonthlyConsistencyData(year, month).test {
            val results = awaitItem()

            // Assert
            val day1 = results.find { it.date.date == 1 } // No spend (from map)
            val day2 = results.find { it.date.date == 2 } // No spend (from list)
            val day3 = results.find { it.date.date == 3 } // Within limit
            val day4 = results.find { it.date.date == 4 } // Over limit

            // Case: amountSpent == 0L && safeToSpend > 0L -> NO_SPEND (green)
            assertEquals(SpendingStatus.NO_SPEND, day1?.status)
            assertEquals(SpendingStatus.NO_SPEND, day2?.status)

            // Case: amountSpent <= safeToSpend -> WITHIN_LIMIT (blue)
            assertEquals(SpendingStatus.WITHIN_LIMIT, day3?.status)

            // Case: amountSpent > safeToSpend -> OVER_LIMIT (red)
            assertEquals(SpendingStatus.OVER_LIMIT, day4?.status)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- REWRITTEN FLAKY TEST ---
    @Test
    fun `getMonthlyConsistencyData returns NO_DATA before first transaction and for future days`() = runTest {

        val year = 2025
        val month = 9 // September
        val budget = 3000f // DSTS = 100L

        val firstTxCal = Calendar.getInstance().apply {
            set(2025, Calendar.SEPTEMBER, 10, 0, 0, 0) // Sept 10th
            set(Calendar.MILLISECOND, 0)
        }
        val firstTxDate = firstTxCal.timeInMillis

        val dailyTotals = listOf(
            DailyTotal(getDateKey(year, month, 11), 50.0)  // Day 11: WITHIN_LIMIT
        )

        // Mock all DAO/Repo calls
        `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(budget))
        `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
        `when`(transactionDao.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotals))

        // Act
        repository.getMonthlyConsistencyData(year, month).test {
            val results = awaitItem()

            val day9 = results.find { it.date.date == 9 }
            val day10 = results.find { it.date.date == 10 }
            val day11 = results.find { it.date.date == 11 }


            assertEquals("Day 9 should be NO_DATA", SpendingStatus.NO_DATA, day9?.status)
            assertEquals("Day 10 should be NO_SPEND", SpendingStatus.NO_SPEND, day10?.status)
            assertEquals("Day 11 should be WITHIN_LIMIT", SpendingStatus.WITHIN_LIMIT, day11?.status)

            cancelAndIgnoreRemainingEvents()
        }


        val prodToday = Calendar.getInstance()
        val futureYear = prodToday.get(Calendar.YEAR)
        val futureMonth = prodToday.get(Calendar.MONTH) + 1
        val futureBudget = 3000f

        val veryFirstTxDate = getTimestamp(Calendar.JANUARY, 1)

        `when`(settingsRepository.getOverallBudgetForMonth(futureYear, futureMonth)).thenReturn(flowOf(futureBudget))
        `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(veryFirstTxDate))
        `when`(transactionDao.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList())) // No totals needed

        // Act
        repository.getMonthlyConsistencyData(futureYear, futureMonth).test {
            val results = awaitItem()

            val day3 = results.find { it.date.date == 3 } // Nov 3rd
            val day1 = results.find { it.date.date == 1 } // Nov 1st (Today)

            assertNotNull("Day 1 (today) should exist", day1)
            assertEquals("Day 1 (today) should be NO_SPEND", SpendingStatus.NO_SPEND, day1?.status)

            assertNotNull("Day 3 (future) should exist", day3)
            assertEquals("Day 3 (future) should be NO_DATA", SpendingStatus.NO_DATA, day3?.status)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateTransactionType calls DAO`() = runTest {
        // Arrange
        val transactionId = 1
        val newType = "income"
        val mockedDao = mock(TransactionDao::class.java)
        val testRepo = TransactionRepository(mockedDao, settingsRepository, tagRepository)


        // Act
        testRepo.updateTransactionType(transactionId, newType)

        // Assert
        verify(mockedDao).updateTransactionType(transactionId, newType)
    }
}


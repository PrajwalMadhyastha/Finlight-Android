// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/data/repository/TransactionRepositoryTest.kt
// REASON: FEATURE (Test) - Added a comprehensive test suite for the new
// `getMonthlyConsistencyData` function. These tests validate all logic
// branches, including the fixes for the "No Budget" (null) and "Zero Budget" (0f)
// edge cases, ensuring heatmap data is calculated correctly.
// =================================================================================
package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
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
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionRepositoryTest : BaseViewModelTest() {

    @Mock
    private lateinit var transactionDao: TransactionDao
    @Mock
    private lateinit var settingsRepository: SettingsRepository
    @Mock
    private lateinit var tagRepository: TagRepository

    private lateinit var repository: TransactionRepository

    // Use a fixed "today" for all tests to make them deterministic
    private val today = Calendar.getInstance().apply {
        set(2025, Calendar.OCTOBER, 28)
    }

    // Helper to get a timestamp for a specific day in 2025
    private fun getTimestamp(month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, 2025)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
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
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
    }

    @Test
    fun `insertTransactionWithTags without travel mode saves transaction and initial tags`() = runTest {
        // Arrange
        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>
        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        // Act
        repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        verify(transactionDao).insert(transaction)
        verify(transactionDao).addTagsToTransaction(capture(crossRefCaptor))
        val capturedRefs = crossRefCaptor.value
        assertEquals(1, capturedRefs.size)
        assertEquals(1, capturedRefs.first().tagId)
        assertEquals(1, capturedRefs.first().transactionId)
    }

    @Test
    fun `insertTransactionWithTags with active travel mode adds trip tag`() = runTest {
        // Arrange
        val tripTag = Tag(id = 99, name = "US Trip")
        val travelSettings = TravelModeSettings(isEnabled = true, tripName = "US Trip", tripType = TripType.DOMESTIC, startDate = 0L, endDate = Long.MAX_VALUE, currencyCode = null, conversionRate = null)
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))
        `when`(tagRepository.findOrCreateTag("US Trip")).thenReturn(tripTag)
        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>


        // Re-initialize repository to pick up the mocked settings
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)

        // Act
        repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        verify(tagRepository).findOrCreateTag("US Trip")
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
        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>


        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)

        // Act
        repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        verify(tagRepository, never()).findOrCreateTag(anyString())
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
    fun `searchMerchants calls DAO`() {
        val query = "amzn"
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
    fun `getTransactionsByTagId calls DAO`() {
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

    @Test
    fun `getMonthlyConsistencyData returns NO_DATA for future days and before first transaction`() = runTest {
        // Arrange (Testing for October 2025, "today" is Oct 28)
        val year = 2025
        val month = 10 // October
        val firstTxDate = getTimestamp(Calendar.OCTOBER, 3) // First tx on Oct 3rd
        val budget = 3100f // DSTS = 100L

        `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(budget))
        `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
        val dailyTotals = listOf(
            DailyTotal(getDateKey(year, month, 4), 50.0) // Spending on Oct 4th
        )
        `when`(transactionDao.getDailySpendingForDateRange(anyLong(), anyLong())).thenReturn(flowOf(dailyTotals))

        // Act
        repository.getMonthlyConsistencyData(year, month).test {
            val results = awaitItem()

            // Assert
            // Find days using date component (day of month)
            val day1 = results.find { it.date.date == 1 }
            val day2 = results.find { it.date.date == 2 }
            val day3 = results.find { it.date.date == 3 }
            val day4 = results.find { it.date.date == 4 }
            val day29 = results.find { it.date.date == 29 }
            val day30 = results.find { it.date.date == 30 }

            // Case: Before first transaction date
            assertEquals(SpendingStatus.NO_DATA, day1?.status)
            assertEquals(SpendingStatus.NO_DATA, day2?.status)

            // Case: On first transaction date (no spending)
            assertEquals(SpendingStatus.NO_SPEND, day3?.status)

            // Case: Valid past day with spending
            assertEquals(SpendingStatus.WITHIN_LIMIT, day4?.status)

            // Case: Future days (relative to Oct 28)
            assertEquals(SpendingStatus.NO_DATA, day29?.status)
            assertEquals(SpendingStatus.NO_DATA, day30?.status)

            cancelAndIgnoreRemainingEvents()
        }
    }
}

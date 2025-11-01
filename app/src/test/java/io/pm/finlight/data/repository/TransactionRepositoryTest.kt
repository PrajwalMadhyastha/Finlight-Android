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
//
// REFACTOR (Test): This entire test file has been refactored to use Mocks
// instead of a real database (`DatabaseTestRule`). This isolates the
// repository's business logic, makes tests faster, and aligns with all
// other repository tests in the project.
//
// FEATURE (Test): Added tests for all single-update methods
// (updateDescription, updateAmount, etc.) and batch-update methods
// (updateCategoryForIds, etc.).
//
// NEW: Added tests for core UI flows (allTransactions, recentTransactions, etc.)
// to fill the coverage gap identified in the review.
//
// FIX (Test): Changed `repository` to a `lateinit var` and initialize it
// within each test. This ensures that mocks for properties (`allTransactions`,
// `recentTransactions`) are in place *before* the repository is constructed,
// fixing `NullPointerException` failures in Turbine tests.
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
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.junit.JUnitAsserter.assertNotNull

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
        // --- FIX: Do NOT initialize repository here. It will be initialized in each test. ---
        // repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
    }

    /**
     * Helper to set up default mocks for repository properties for tests that
     * don't care about them.
     */
    private fun setupDefaultPropertyMocks() {
        `when`(transactionDao.getAllTransactions()).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getRecentTransactionDetails()).thenReturn(flowOf(emptyList()))
    }

    @Test
    fun `insertTransactionWithTags without travel mode saves transaction and initial tags`() = runTest {
        // Arrange
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>

        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        // Act
        val newId = repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        verify(transactionDao).insert(transaction)
        // --- FIX: Use captor.capture() with an elvis operator to avoid NPE ---
        verify(transactionDao).addTagsToTransaction(crossRefCaptor.capture() ?: emptyList())

        val capturedRefs = crossRefCaptor.value
        assertEquals(1L, newId)
        assertEquals(1, capturedRefs.size)
        assertEquals(1, capturedRefs.first().tagId)
        assertEquals(newId.toInt(), capturedRefs.first().transactionId)
    }

    @Test
    fun `insertTransactionWithTags with active travel mode adds trip tag`() = runTest {
        // Arrange
        setupDefaultPropertyMocks() // Add default mocks for properties
        val tripTag = Tag(id = 99, name = "US Trip")
        val travelSettings = TravelModeSettings(isEnabled = true, tripName = "US Trip", tripType = TripType.DOMESTIC, startDate = 0L, endDate = Long.MAX_VALUE, currencyCode = null, conversionRate = null)
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))
        `when`(tagRepository.findOrCreateTag("US Trip")).thenReturn(tripTag)

        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>

        // Act
        repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        verify(tagRepository).findOrCreateTag("US Trip")
        // --- FIX: Use captor.capture() with an elvis operator to avoid NPE ---
        verify(transactionDao).addTagsToTransaction(crossRefCaptor.capture() ?: emptyList())
        val capturedRefs = crossRefCaptor.value
        assertEquals(2, capturedRefs.size) // Work tag + Trip tag
        assertTrue(capturedRefs.any { it.tagId == 1 })
        assertTrue(capturedRefs.any { it.tagId == 99 })
    }

    @Test
    fun `insertTransactionWithTags with inactive travel mode does not add trip tag`() = runTest {
        // Arrange
        setupDefaultPropertyMocks() // Add default mocks for properties
        val travelSettings = TravelModeSettings(isEnabled = false, tripName = "US Trip", tripType = TripType.DOMESTIC, startDate = 0L, endDate = Long.MAX_VALUE, currencyCode = null, conversionRate = null)
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))

        `when`(transactionDao.insert(anyObject())).thenReturn(1L)

        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val initialTags = setOf(Tag(id = 1, name = "Work"))
        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>

        // Act
        repository.insertTransactionWithTags(transaction, initialTags)

        // Assert
        verify(tagRepository, never()).findOrCreateTag(anyString())
        // --- FIX: Use captor.capture() with an elvis operator to avoid NPE ---
        verify(transactionDao).addTagsToTransaction(crossRefCaptor.capture() ?: emptyList())
        val capturedRefs = crossRefCaptor.value
        assertEquals(1, capturedRefs.size) // Only Work tag
        assertEquals(1, capturedRefs.first().tagId)
    }

    // --- NEW: Test for insertTransactionWithTagsAndImages ---
    @Test
    fun `insertTransactionWithTagsAndImages saves transaction, tags, and images`() = runTest {
        // Arrange
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

        val transaction = Transaction(description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val tags = setOf(Tag(id = 1, name = "Work"))
        val imagePaths = listOf("path/to/image1.jpg", "path/to/image2.jpg")
        val newTxId = 5L

        `when`(transactionDao.insert(anyObject())).thenReturn(newTxId)
        @Suppress("UNCHECKED_CAST")
        val tagsCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>
        val imageCaptor = ArgumentCaptor.forClass(TransactionImage::class.java)

        // Act
        val resultId = repository.insertTransactionWithTagsAndImages(transaction, tags, imagePaths)

        // Assert
        assertEquals(newTxId, resultId)
        verify(transactionDao).insert(transaction)
        // --- FIX: Use captor.capture() with an elvis operator to avoid NPE ---
        verify(transactionDao).addTagsToTransaction(tagsCaptor.capture() ?: emptyList())
        assertEquals(1, tagsCaptor.value.size)
        assertEquals(newTxId.toInt(), tagsCaptor.value.first().transactionId)
        // --- FIX: Use captor.capture() with an elvis operator to avoid NPE ---
        verify(transactionDao, times(2)).insertImage(imageCaptor.capture() ?: TransactionImage(transactionId = 0, imageUri = ""))

        val capturedImages = imageCaptor.allValues
        assertEquals(2, capturedImages.size)
        assertEquals(newTxId.toInt(), capturedImages[0].transactionId)
        assertEquals("path/to/image1.jpg", capturedImages[0].imageUri)
        assertEquals("path/to/image2.jpg", capturedImages[1].imageUri)
    }

    // --- NEW: Test for updateTransactionWithTags ---
    @Test
    fun `updateTransactionWithTags updates transaction and replaces tags`() = runTest {
        // Arrange
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

        val transaction = Transaction(id = 1, description = "Test", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null)
        val newTags = setOf(Tag(id = 2, name = "Vacation"))

        @Suppress("UNCHECKED_CAST")
        val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>

        // Act
        repository.updateTransactionWithTags(transaction, newTags)

        // Assert
        verify(transactionDao).update(transaction)
        verify(transactionDao).clearTagsForTransaction(transaction.id)
        // --- FIX: Use captor.capture() with an elvis operator to avoid NPE ---
        verify(transactionDao).addTagsToTransaction(crossRefCaptor.capture() ?: emptyList())
        val capturedRefs = crossRefCaptor.value
        assertEquals(1, capturedRefs.size)
        assertEquals(2, capturedRefs.first().tagId) // Assert new tag
    }

    // --- NEW: Test for delete ---
    @Test
    fun `delete calls DAO`() = runTest {
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize
        val transaction = Transaction(id = 1, description = "Test", amount = 1.0, date = 0L, accountId = 1, categoryId = 1, notes = null)
        repository.delete(transaction)
        verify(transactionDao).delete(transaction)
    }

    // --- NEW: Test for setSmsHash ---
    @Test
    fun `setSmsHash calls DAO`() = runTest {
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize
        val hash = "testhash"
        repository.setSmsHash(1, hash)
        verify(transactionDao).setSmsHash(1, hash)
    }

    // --- NEW: Test for getTransactionCountForMerchant ---
    @Test
    fun `getTransactionCountForMerchant calls DAO`() = runTest {
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize
        val desc = "Amazon"
        `when`(transactionDao.getTransactionCountForMerchant(desc)).thenReturn(flowOf(5))
        repository.getTransactionCountForMerchant(desc).test {
            assertEquals(5, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getTransactionCountForMerchant(desc)
    }

    // --- NEW: Test for findSimilarTransactions ---
    @Test
    fun `findSimilarTransactions calls DAO`() = runTest {
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize
        val desc = "Amazon"
        repository.findSimilarTransactions(desc, 1)
        verify(transactionDao).findSimilarTransactions(desc, 1)
    }

    // --- NEW: Test for updateCategoryForIds ---
    @Test
    fun `updateCategoryForIds calls DAO`() = runTest {
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize
        val ids = listOf(1, 2)
        val categoryId = 5
        repository.updateCategoryForIds(ids, categoryId)
        verify(transactionDao).updateCategoryForIds(ids, categoryId)
    }

    // --- NEW: Test for updateDescriptionForIds ---
    @Test
    fun `updateDescriptionForIds calls DAO`() = runTest {
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize
        val ids = listOf(1, 2)
        val newDesc = "New Description"
        repository.updateDescriptionForIds(ids, newDesc)
        verify(transactionDao).updateDescriptionForIds(ids, newDesc)
    }


    // --- NEW: Tests for individual update methods ---

    @Test
    fun `updateDescription calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        repository.updateDescription(1, "New Desc")
        verify(transactionDao).updateDescription(1, "New Desc")
    }

    @Test
    fun `updateAmount calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        repository.updateAmount(1, 123.45)
        verify(transactionDao).updateAmount(1, 123.45)
    }

    @Test
    fun `updateNotes calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        repository.updateNotes(1, "New Note")
        verify(transactionDao).updateNotes(1, "New Note")
    }

    @Test
    fun `updateCategoryId calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        repository.updateCategoryId(1, 5)
        verify(transactionDao).updateCategoryId(1, 5)
    }

    @Test
    fun `updateAccountId calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        repository.updateAccountId(1, 2)
        verify(transactionDao).updateAccountId(1, 2)
    }

    @Test
    fun `updateDate calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        repository.updateDate(1, 999L)
        verify(transactionDao).updateDate(1, 999L)
    }

    @Test
    fun `updateExclusionStatus calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        repository.updateExclusionStatus(1, true)
        verify(transactionDao).updateExclusionStatus(1, true)
    }

    @Test
    fun `updateTransactionType calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        repository.updateTransactionType(1, "income")
        verify(transactionDao).updateTransactionType(1, "income")
    }

    @Test
    fun `getTotalExpensesSince calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        val startDate = 1000L
        repository.getTotalExpensesSince(startDate)
        verify(transactionDao).getTotalExpensesSince(startDate)
    }

    @Test
    fun `searchMerchants calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        val query = "amzn"
        `when`(transactionDao.searchMerchants(query)).thenReturn(flowOf(emptyList()))
        repository.searchMerchants(query)
        verify(transactionDao).searchMerchants(query)
    }

    @Test
    fun `deleteByIds calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        val ids = listOf(1, 2)
        repository.deleteByIds(ids)
        verify(transactionDao).deleteByIds(ids)
    }

    @Test
    fun `addTagForDateRange calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        repository.addTagForDateRange(1, 100L, 200L)
        verify(transactionDao).addTagForDateRange(1, 100L, 200L)
    }

    @Test
    fun `removeTagForDateRange calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        repository.removeTagForDateRange(1, 100L, 200L)
        verify(transactionDao).removeTagForDateRange(1, 100L, 200L)
    }

    @Test
    fun `getTransactionsByTagId calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        `when`(transactionDao.getTransactionsByTagId(1)).thenReturn(flowOf(emptyList()))
        repository.getTransactionsByTagId(1)
        verify(transactionDao).getTransactionsByTagId(1)
    }

    @Test
    fun `removeAllTransactionsForTag calls DAO`() = runTest {
        setupDefaultPropertyMocks()
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository)
        repository.removeAllTransactionsForTag(1)
        verify(transactionDao).removeAllTransactionsForTag(1)
    }

    // --- NEW TESTS FOR getMonthlyConsistencyData ---

    @Test
    fun `getMonthlyConsistencyData returns NO_DATA for all past days if budget is null`() = runTest {
        // Arrange
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

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
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

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
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

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
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

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

        // --- Test for future days ---
        // Use the *real* "today" from the test runner's environment
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

            // Find today
            val todayDay = prodToday.get(Calendar.DAY_OF_MONTH)
            val todayData = results.find { it.date.date == todayDay }
            assertNotNull("Today's data (day $todayDay) should exist", todayData)
            assertEquals("Today (day $todayDay) should be NO_SPEND", SpendingStatus.NO_SPEND, todayData?.status)

            // Find a future day (if one exists in this month)
            val futureDay = prodToday.get(Calendar.DAY_OF_MONTH) + 2
            val daysInCurrentMonth = prodToday.getActualMaximum(Calendar.DAY_OF_MONTH)

            if (futureDay <= daysInCurrentMonth) {
                val futureDayData = results.find { it.date.date == futureDay }
                assertNotNull("Future day ($futureDay) should exist", futureDayData)
                assertEquals("Future day ($futureDay) should be NO_DATA", SpendingStatus.NO_DATA, futureDayData?.status)
            }


            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Tests for core UI flows ---
    @Test
    fun `allTransactions flow emits data from DAO`() = runTest {
        // Arrange
        val mockDetails = listOf(TransactionDetails(Transaction(id = 1, description = "Test", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null), emptyList(), null, null, null, null, null))
        `when`(transactionDao.getAllTransactions()).thenReturn(flowOf(mockDetails))
        `when`(transactionDao.getRecentTransactionDetails()).thenReturn(flowOf(emptyList())) // Default for other prop

        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize HERE

        // Act & Assert
        repository.allTransactions.test {
            assertEquals(mockDetails, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getAllTransactions()
    }

    @Test
    fun `recentTransactions flow emits data from DAO`() = runTest {
        // Arrange
        val mockDetails = listOf(TransactionDetails(Transaction(id = 1, description = "Recent", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null), emptyList(), null, null, null, null, null))
        `when`(transactionDao.getRecentTransactionDetails()).thenReturn(flowOf(mockDetails))
        `when`(transactionDao.getAllTransactions()).thenReturn(flowOf(emptyList())) // Default for other prop

        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize HERE

        // Act & Assert
        repository.recentTransactions.test {
            assertEquals(mockDetails, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getRecentTransactionDetails()
    }

    @Test
    fun `getFinancialSummaryForRangeFlow emits data from DAO`() = runTest {
        // Arrange
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

        val mockSummary = FinancialSummary(1000.0, 500.0)
        `when`(transactionDao.getFinancialSummaryForRangeFlow(100L, 200L)).thenReturn(flowOf(mockSummary))

        // Act & Assert
        repository.getFinancialSummaryForRangeFlow(100L, 200L).test {
            assertEquals(mockSummary, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getFinancialSummaryForRangeFlow(100L, 200L)
    }

    @Test
    fun `getSpendingByCategoryForMonth emits data from DAO`() = runTest {
        // Arrange
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

        val mockSpending = listOf(CategorySpending("Food", 100.0, "red", "icon"))
        `when`(transactionDao.getSpendingByCategoryForMonth(100L, 200L, "keyword", 1, 2)).thenReturn(flowOf(mockSpending))

        // Act & Assert
        repository.getSpendingByCategoryForMonth(100L, 200L, "keyword", 1, 2).test {
            assertEquals(mockSpending, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getSpendingByCategoryForMonth(100L, 200L, "keyword", 1, 2)
    }

    @Test
    fun `getMonthlyTrends emits data from DAO`() = runTest {
        // Arrange
        setupDefaultPropertyMocks() // Add default mocks for properties
        repository = TransactionRepository(transactionDao, settingsRepository, tagRepository) // Initialize

        val mockTrends = listOf(MonthlyTrend("2025-10", 1000.0, 500.0))
        `when`(transactionDao.getMonthlyTrends(123L)).thenReturn(flowOf(mockTrends))

        // Act & Assert
        repository.getMonthlyTrends(123L).test {
            assertEquals(mockTrends, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(transactionDao).getMonthlyTrends(123L)
    }
}


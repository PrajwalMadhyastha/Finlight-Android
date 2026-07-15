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

import androidx.room.withTransaction
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.DeletedSmsHashDao
import io.pm.finlight.data.db.dao.MergeRecordDao

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
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.robolectric.annotation.Config
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.junit.JUnitAsserter.assertNotNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionRepositoryTest : BaseViewModelTest() {

    // ── Fields ───────────────────────────────────────────────────────────────

    @Mock
    private lateinit var transactionDao: TransactionDao

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var tagRepository: TagRepository

    @Mock
    private lateinit var deletedSmsHashDao: DeletedSmsHashDao

    @Mock
    private lateinit var mergeRecordDao: MergeRecordDao

    @Mock
    private lateinit var db: AppDatabase

    private lateinit var repository: TransactionRepository

    // Use a fixed "today" for all tests to make them deterministic
    // This is for the test's *logic*, not the production code's `today`
    private val testToday =
        Calendar.getInstance().apply {
            set(2025, Calendar.OCTOBER, 28, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

    // Helper to get a timestamp for a specific day in 2025
    // --- FIX: This function now zeroes out the time, matching the repo's logic ---
    private fun getTimestamp(
        month: Int,
        day: Int,
    ): Long {
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
    private fun getDateKey(
        year: Int,
        month: Int,
        day: Int,
    ): String {
        return String.format(Locale.ROOT, "%d-%02d-%02d", year, month, day)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Before
    override fun setup() {
        super.setup()
        // Initialize with default mock behaviors
        `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(null))

        // Mock withTransaction
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<AppDatabase>().withTransaction<Any?>(any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }
    }

    @org.junit.After
    override fun tearDown() {
        unmockkAll()
    }

    /**
     * Helper to set up default mocks for repository properties for tests that
     * don't care about them.
     */
    private fun setupDefaultPropertyMocks() {
        `when`(transactionDao.getAllTransactions()).thenReturn(flowOf(emptyList()))
        `when`(transactionDao.getRecentTransactionDetails()).thenReturn(flowOf(emptyList()))
    }

    // ── Tests: Manual Merge ───────────────────────────────────────────────────

    @Test
    fun `manualMergeTransactions successfully merges multiple children into anchor`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)

            val anchor = Transaction(id = 1, description = "Anchor", amount = 100.0, date = 1000L, accountId = 1, categoryId = 1, notes = "Anchor note", transactionType = "expense")
            val child1 = Transaction(id = 2, description = "Child 1", amount = 50.0, date = 2000L, accountId = 1, categoryId = 2, notes = "Child note", transactionType = "expense")
            val child2 = Transaction(id = 3, description = "Child 2", amount = -20.0, date = 3000L, accountId = 1, categoryId = 3, notes = null, transactionType = "income")

            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(anchor)
            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(child1)
            `when`(transactionDao.getTransactionByIdSync(3)).thenReturn(child2)

            `when`(transactionDao.getTagsForTransactionSimple(1)).thenReturn(listOf(Tag(id = 1, name = "A")))
            `when`(transactionDao.getTagsForTransactionSimple(2)).thenReturn(listOf(Tag(id = 2, name = "B")))
            `when`(transactionDao.getTagsForTransactionSimple(3)).thenReturn(emptyList())

            repository.manualMergeTransactions(anchor.id, listOf(child1.id, child2.id))

            // Verify children are deleted
            verify(transactionDao).delete(child1)
            verify(transactionDao).delete(child2)

            // anchor(expense)=-100, child1(expense)=-50, child2(income, amount=-20.0) -> signed=-20.0
            // netSigned = -100 + -50 + -20 = -170  =>  finalAmount = abs(-170) = 170.0
            verify(transactionDao).updateAmount(1, 170.0)
            verify(transactionDao).updateNotes(org.mockito.kotlin.eq(1), org.mockito.kotlin.any())

            // Verify merge records are created
            verify(mergeRecordDao, org.mockito.kotlin.times(2)).insert(org.mockito.kotlin.any())
        }

    @Test
    fun `unmergeTransactions correctly unmerges a manual merge group`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)

            val anchor = Transaction(id = 1, description = "Anchor", amount = 130.0, date = 1000L, accountId = 1, categoryId = 1, notes = "Anchor note\nChild note", transactionType = "expense")

            // The merge record exists for the anchor
            val groupRecord =
                io.pm.finlight.data.db.entity.MergeRecord(
                    id = 10, parentTxnId = 1, mergedAt = 0L, mergeGroupId = "group-123", mergeType = "MANUAL",
                    originalParentAmount = 100.0, originalParentDate = 1000L, originalParentNotes = "Anchor note",
                    childDescription = "Child 1", childAmount = 50.0, childDate = 2000L, childAccountId = 1,
                    childCategoryId = 2, childTransactionType = "expense", childSource = "MANUAL", childNotes = "Child note",
                    childSourceSmsId = null, childSourceSmsHash = null, childSmsSignature = null,
                    childOriginalDescription = null, childOriginalAmount = null, childCurrencyCode = null, childConversionRate = null
                )

            `when`(mergeRecordDao.getForParentSync(1)).thenReturn(groupRecord)
            `when`(mergeRecordDao.getAllForGroup("group-123")).thenReturn(listOf(groupRecord))

            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(anchor)

            repository.unmergeTransactions(1)

            // Verify anchor was restored
            verify(transactionDao).updateAmount(1, 100.0)
            verify(transactionDao).updateNotes(1, "Anchor note")

            // Verify child was inserted back
            verify(transactionDao).insert(org.mockito.kotlin.any())

            // Verify record deleted
            verify(mergeRecordDao).deleteByGroupId("group-123")
        }

    // ── Tests: Core Insert / Update / Delete ──────────────────────────────────

    @Test
    fun `insertTransactionWithTags without travel mode saves transaction and initial tags`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val transaction =
                Transaction(
                    description = "Test",
                    amount = 100.0,
                    date = System.currentTimeMillis(),
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
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
    fun `insertTransactionWithTags with active travel mode adds trip tag`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            val tripTag = Tag(id = 99, name = "US Trip")
            val travelSettings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "US Trip",
                    tripType = TripType.DOMESTIC,
                    startDate = 0L,
                    endDate = Long.MAX_VALUE,
                    currencyCode = null,
                    conversionRate = null,
                )
            `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))
            `when`(tagRepository.findOrCreateTag("US Trip")).thenReturn(tripTag)

            `when`(transactionDao.insert(anyObject())).thenReturn(1L)

            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val transaction =
                Transaction(
                    description = "Test",
                    amount = 100.0,
                    date = System.currentTimeMillis(),
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
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
    fun `insertTransactionWithTags with inactive travel mode does not add trip tag`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            val travelSettings =
                TravelModeSettings(
                    isEnabled = false,
                    tripName = "US Trip",
                    tripType = TripType.DOMESTIC,
                    startDate = 0L,
                    endDate = Long.MAX_VALUE,
                    currencyCode = null,
                    conversionRate = null,
                )
            `when`(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))

            `when`(transactionDao.insert(anyObject())).thenReturn(1L)

            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val transaction =
                Transaction(
                    description = "Test",
                    amount = 100.0,
                    date = System.currentTimeMillis(),
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
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
    fun `insertTransactionWithTagsAndImages saves transaction, tags, and images`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val transaction =
                Transaction(
                    description = "Test",
                    amount = 100.0,
                    date = System.currentTimeMillis(),
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
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
    fun `updateTransactionWithTags updates transaction and replaces tags`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val transaction =
                Transaction(
                    id = 1,
                    description = "Test",
                    amount = 100.0,
                    date = System.currentTimeMillis(),
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
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
    fun `delete calls DAO`() =
        runTest {
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize
            val transaction =
                Transaction(id = 1, description = "Test", amount = 1.0, date = 0L, accountId = 1, categoryId = 1, notes = null)
            repository.delete(transaction)
            verify(transactionDao).delete(transaction)
        }

    // --- NEW: Test for setSmsHash ---
    @Test
    fun `setSmsHash calls DAO`() =
        runTest {
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize
            val hash = "testhash"
            repository.setSmsHash(1, hash)
            verify(transactionDao).setSmsHash(1, hash)
        }

    // --- NEW: Test for getTransactionCountForMerchant ---
    @Test
    fun `getTransactionCountForMerchant calls DAO`() =
        runTest {
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize
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
    fun `findSimilarTransactions calls DAO`() =
        runTest {
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize
            val desc = "Amazon"
            repository.findSimilarTransactions(desc, 1)
            verify(transactionDao).findSimilarTransactions(desc, 1)
        }

    // --- NEW: Test for updateCategoryForIds ---
    @Test
    fun `updateCategoryForIds calls DAO`() =
        runTest {
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize
            val ids = listOf(1, 2)
            val categoryId = 5
            repository.updateCategoryForIds(ids, categoryId)
            verify(transactionDao).updateCategoryForIds(ids, categoryId)
        }

    // --- NEW: Test for updateDescriptionForIds ---
    @Test
    fun `updateDescriptionForIds calls DAO`() =
        runTest {
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize
            val ids = listOf(1, 2)
            val newDesc = "New Description"
            repository.updateDescriptionForIds(ids, newDesc)
            verify(transactionDao).updateDescriptionForIds(ids, newDesc)
        }

    // --- NEW: Tests for individual update methods ---

    @Test
    fun `clearReviewFlag calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.clearReviewFlag(1)
            verify(transactionDao).clearReviewFlag(1)
        }

    @Test
    fun `updateDescription calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.updateDescription(1, "New Desc")
            verify(transactionDao).updateDescription(1, "New Desc")
        }

    @Test
    fun `updateAmount calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.updateAmount(1, 123.45)
            verify(transactionDao).updateAmount(1, 123.45)
        }

    @Test
    fun `updateNotes calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.updateNotes(1, "New Note")
            verify(transactionDao).updateNotes(1, "New Note")
        }

    @Test
    fun `updateCategoryId calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.updateCategoryId(1, 5)
            verify(transactionDao).updateCategoryId(1, 5)
        }

    @Test
    fun `updateAccountId calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.updateAccountId(1, 2)
            verify(transactionDao).updateAccountId(1, 2)
        }

    @Test
    fun `updateDate calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.updateDate(1, 999L)
            verify(transactionDao).updateDate(1, 999L)
        }

    @Test
    fun `updateExclusionStatus calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.updateExclusionStatus(1, true)
            verify(transactionDao).updateExclusionStatus(1, true)
        }

    @Test
    fun `updateTransactionType calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.updateTransactionType(1, "income")
            verify(transactionDao).updateTransactionType(1, "income")
        }

    @Test
    fun `getTotalExpensesSince calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            val startDate = 1000L
            repository.getTotalExpensesSince(startDate)
            verify(transactionDao).getTotalExpensesSince(startDate)
        }

    @Test
    fun `searchMerchants calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            val query = "amzn"
            `when`(transactionDao.searchMerchants(query)).thenReturn(flowOf(emptyList()))
            repository.searchMerchants(query)
            verify(transactionDao).searchMerchants(query)
        }

    @Test
    fun `deleteByIds calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            val ids = listOf(1, 2)
            repository.deleteByIds(ids)
            verify(transactionDao).deleteByIds(ids)
        }

    @Test
    fun `getRecentManualTransactions calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            `when`(transactionDao.getRecentManualTransactions(5)).thenReturn(flowOf(emptyList()))
            repository.getRecentManualTransactions(5)
            verify(transactionDao).getRecentManualTransactions(5)
        }

    @Test
    fun `addTagForDateRange calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.addTagForDateRange(1, 100L, 200L)
            verify(transactionDao).addTagForDateRange(1, 100L, 200L)
        }

    @Test
    fun `removeTagForDateRange calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.removeTagForDateRange(1, 100L, 200L)
            verify(transactionDao).removeTagForDateRange(1, 100L, 200L)
        }

    @Test
    fun `getTransactionsByTagId calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            `when`(transactionDao.getTransactionsByTagId(1)).thenReturn(flowOf(emptyList()))
            repository.getTransactionsByTagId(1)
            verify(transactionDao).getTransactionsByTagId(1)
        }

    @Test
    fun `removeAllTransactionsForTag calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.removeAllTransactionsForTag(1)
            verify(transactionDao).removeAllTransactionsForTag(1)
        }

    // --- NEW TESTS FOR getMonthlyConsistencyData ---

    @Test
    fun `getMonthlyConsistencyData returns NO_DATA for all past days if budget is null`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val year = 2025
            val month = 9 // September
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

            `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(null))
            `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
            // Mock spending on day 2 and no spending on day 3
            val dailyTotals =
                listOf(
                    DailyTotal(getDateKey(year, month, 2), 100.0),
                    DailyTotal(getDateKey(year, month, 3), 0.0),
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
    fun `getMonthlyConsistencyData handles 0f budget correctly`() =
        runTest {
            // Arrange (Testing for September 2025)
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val year = 2025
            val month = 9 // September
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)

            `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(0f)) // Zero budget
            `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
            val dailyTotals =
                listOf(
                    // Day 2: Spent 100
                    DailyTotal(getDateKey(year, month, 2), 100.0),
                    // Day 3: Spent 0
                    DailyTotal(getDateKey(year, month, 3), 0.0),
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
    fun `getMonthlyConsistencyData handles positive budget correctly`() =
        runTest {
            // Arrange (Testing for September 2025, 30 days)
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val year = 2025
            val month = 9 // September
            val firstTxDate = getTimestamp(Calendar.SEPTEMBER, 1)
            val budget = 3000f // DSTS = 100L
            val safeToSpend = 100L

            `when`(settingsRepository.getOverallBudgetForMonth(year, month)).thenReturn(flowOf(budget))
            `when`(transactionDao.getFirstTransactionDate()).thenReturn(flowOf(firstTxDate))
            val dailyTotals =
                listOf(
                    // No spend
                    DailyTotal(getDateKey(year, month, 2), 0.0),
                    // Within limit
                    DailyTotal(getDateKey(year, month, 3), 50.0),
                    // Over limit
                    DailyTotal(getDateKey(year, month, 4), 150.0),
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
    fun `getMonthlyConsistencyData returns NO_DATA before first transaction and for future days`() =
        runTest {
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val year = 2025
            val month = 9 // September
            val budget = 3000f // DSTS = 100L

            val firstTxCal =
                Calendar.getInstance().apply {
                    set(2025, Calendar.SEPTEMBER, 10, 0, 0, 0) // Sept 10th
                    set(Calendar.MILLISECOND, 0)
                }
            val firstTxDate = firstTxCal.timeInMillis

            val dailyTotals =
                listOf(
                    // Day 11: WITHIN_LIMIT
                    DailyTotal(getDateKey(year, month, 11), 50.0),
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
    fun `allTransactions flow emits data from DAO`() =
        runTest {
            // Arrange
            val mockDetails =
                listOf(
                    TransactionDetails(
                        Transaction(id = 1, description = "Test", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null),
                        emptyList(),
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                )
            `when`(transactionDao.getAllTransactions()).thenReturn(flowOf(mockDetails))
            `when`(transactionDao.getRecentTransactionDetails()).thenReturn(flowOf(emptyList())) // Default for other prop

            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize HERE

            // Act & Assert
            repository.allTransactions.test {
                assertEquals(mockDetails, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(transactionDao).getAllTransactions()
        }

    @Test
    fun `recentTransactions flow emits data from DAO`() =
        runTest {
            // Arrange
            val mockDetails =
                listOf(
                    TransactionDetails(
                        Transaction(id = 1, description = "Recent", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null),
                        emptyList(),
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                )
            `when`(transactionDao.getRecentTransactionDetails()).thenReturn(flowOf(mockDetails))
            `when`(transactionDao.getAllTransactions()).thenReturn(flowOf(emptyList())) // Default for other prop

            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize HERE

            // Act & Assert
            repository.recentTransactions.test {
                assertEquals(mockDetails, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(transactionDao).getRecentTransactionDetails()
        }

    @Test
    fun `getFinancialSummaryForRangeFlow emits data from DAO`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

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
    fun `getSpendingByCategoryForMonth emits data from DAO`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val mockSpending = listOf(CategorySpending("Food", 100.0, "red", "icon"))
            `when`(transactionDao.getSpendingByCategoryForMonth(100L, 200L, "keyword", 1, 2, "expense")).thenReturn(flowOf(mockSpending))

            // Act & Assert
            repository.getSpendingByCategoryForMonth(100L, 200L, "keyword", 1, 2, "expense").test {
                assertEquals(mockSpending, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(transactionDao).getSpendingByCategoryForMonth(100L, 200L, "keyword", 1, 2, "expense")
        }

    @Test
    fun `getMonthlyTrends emits data from DAO`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val mockTrends = listOf(MonthlyTrend("2025-10", 1000.0, 500.0))
            `when`(transactionDao.getMonthlyTrends(123L)).thenReturn(flowOf(mockTrends))

            // Act & Assert
            repository.getMonthlyTrends(123L).test {
                assertEquals(mockTrends, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(transactionDao).getMonthlyTrends(123L)
        }

    @Test
    fun `getSpendingByMerchantForMonth emits data from DAO`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db) // Initialize

            val mockSpending = listOf(MerchantSpendingSummary("Amazon", 100.0, 2))
            `when`(transactionDao.getSpendingByMerchantForMonth(100L, 200L, "keyword", 1, 2, "expense")).thenReturn(flowOf(mockSpending))

            // Act & Assert
            repository.getSpendingByMerchantForMonth(100L, 200L, "keyword", 1, 2, "expense").test {
                assertEquals(mockSpending, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(transactionDao).getSpendingByMerchantForMonth(100L, 200L, "keyword", 1, 2, "expense")
        }

    // --- NEW: Tests for Smart Transaction Merge ---
    @Test
    fun `dismissMerge calls DAO updateMergeDismissed`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)
            repository.dismissMerge(1)
            verify(transactionDao).updateMergeDismissed(1, true)
        }

    @Test
    fun `mergeTransactions sums amounts, updates parent, appends notes, and deletes child`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)

            val parentTxn = Transaction(id = 1, description = "Test", amount = 100.0, date = 1000L, accountId = 1, categoryId = 1, notes = "Parent note", transactionType = "expense")
            val childTxn = Transaction(id = 2, description = "Test", amount = 50.0, date = 2000L, accountId = 1, categoryId = 2, notes = "Child note", transactionType = "expense")

            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(parentTxn)
            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(childTxn)

            repository.mergeTransactions(1, 2)

            verify(transactionDao).getTransactionByIdSync(1)
            verify(transactionDao).getTransactionByIdSync(2)

            verify(transactionDao).updateAmount(1, 150.0)
            verify(transactionDao).updateDate(1, 2000L)

            val notesCaptor = ArgumentCaptor.forClass(String::class.java)
            verify(transactionDao).updateNotes(org.mockito.ArgumentMatchers.eq(1), notesCaptor.capture() ?: "")

            val updatedNotes = notesCaptor.value
            assertTrue(updatedNotes.contains("Merged Transaction:"))
            assertTrue(updatedNotes.contains("Parent note"))

            verify(transactionDao).delete(childTxn)
        }

    @Test
    fun `mergeTransactions inserts smsHash when child transaction has sourceSmsHash`() =
        runTest {
            setupDefaultPropertyMocks()
            val deletedSmsHashDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.DeletedSmsHashDao::class.java)
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDaoMock, mergeRecordDao, db)

            val parentTxn = Transaction(id = 1, description = "Test", amount = 100.0, date = 1000L, accountId = 1, categoryId = 1, notes = "Parent note", transactionType = "expense")
            val childTxn = Transaction(id = 2, description = "Test", amount = 50.0, date = 2000L, accountId = 1, categoryId = 2, notes = "Child note", transactionType = "expense", sourceSmsHash = "xyz123")

            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(parentTxn)
            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(childTxn)

            repository.mergeTransactions(1, 2)

            verify(deletedSmsHashDaoMock).insert(io.pm.finlight.data.db.entity.DeletedSmsHash("xyz123"))
        }

    @Test
    fun `mergeTransactions does not insert smsHash when child transaction has no sourceSmsHash`() =
        runTest {
            setupDefaultPropertyMocks()
            val deletedSmsHashDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.DeletedSmsHashDao::class.java)
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDaoMock, mergeRecordDao, db)

            val parentTxn = Transaction(id = 1, description = "Test", amount = 100.0, date = 1000L, accountId = 1, categoryId = 1, notes = "Parent note", transactionType = "expense")
            val childTxn = Transaction(id = 2, description = "Test", amount = 50.0, date = 2000L, accountId = 1, categoryId = 2, notes = "Child note", transactionType = "expense", sourceSmsHash = null)

            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(parentTxn)
            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(childTxn)

            repository.mergeTransactions(1, 2)

            verify(deletedSmsHashDaoMock, org.mockito.Mockito.never()).insert(org.mockito.kotlin.any())
        }

    @Test
    fun `mergeTransactions formats notes correctly when childSmsBody is provided`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)

            val parentTxn = Transaction(id = 1, description = "Test", amount = 100.0, date = 1000L, accountId = 1, categoryId = 1, notes = "Parent note", transactionType = "expense")
            val childTxn = Transaction(id = 2, description = "Test", amount = 50.0, date = 2000L, accountId = 1, categoryId = 2, notes = "Child note", transactionType = "expense")

            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(parentTxn)
            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(childTxn)

            repository.mergeTransactions(1, 2, "Test SMS body", 1000000L)

            val notesCaptor = ArgumentCaptor.forClass(String::class.java)
            verify(transactionDao).updateNotes(org.mockito.ArgumentMatchers.eq(1), notesCaptor.capture() ?: "")

            val updatedNotes = notesCaptor.value
            assertTrue(updatedNotes.contains("Merged on"))
            assertTrue(updatedNotes.contains("Test SMS body"))
            assertTrue(updatedNotes.contains("Parent note"))
        }

    @Test
    fun `linkReimbursement deducts income amount from expense amount`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)

            val expenseTxn = Transaction(id = 1, description = "Expense", amount = 1500.0, date = 1000L, accountId = 1, categoryId = 1, notes = "", transactionType = "expense")
            val incomeTxn = Transaction(id = 2, description = "Income", amount = 500.0, date = 2000L, accountId = 1, categoryId = 2, notes = "", transactionType = "income")

            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            repository.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionDao).linkReimbursement(2, 1)
            verify(transactionDao).updateAmount(1, 1000.0)
        }

    @Test
    fun `linkReimbursement does not set expense amount below zero`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)

            val expenseTxn = Transaction(id = 1, description = "Expense", amount = 300.0, date = 1000L, accountId = 1, categoryId = 1, notes = "", transactionType = "expense")
            val incomeTxn = Transaction(id = 2, description = "Income", amount = 500.0, date = 2000L, accountId = 1, categoryId = 2, notes = "", transactionType = "income")

            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            repository.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionDao).linkReimbursement(2, 1)
            verify(transactionDao).updateAmount(1, 0.0) // coerceAtLeast(0.0)
        }

    @Test
    fun `unlinkReimbursement restores amount to expense`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDao, db)

            val expenseTxn = Transaction(id = 1, description = "Expense", amount = 1000.0, date = 1000L, accountId = 1, categoryId = 1, notes = "", transactionType = "expense")
            val incomeTxn = Transaction(id = 2, description = "Income", amount = 500.0, date = 2000L, accountId = 1, categoryId = 2, notes = "", transactionType = "income", parentReimbursementId = 1)

            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            repository.unlinkReimbursement(incomeId = 2)

            verify(transactionDao).unlinkReimbursement(2)
            verify(transactionDao).updateAmount(1, 1500.0)
        }

    // --- NEW: Tests for Smart Transaction Unmerge (MergeRecord snapshot) ---

    @Test
    fun `mergeTransactions inserts MergeRecord snapshot with correct parent fields before mutation`() =
        runTest {
            setupDefaultPropertyMocks()
            val mergeRecordDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.MergeRecordDao::class.java)
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDaoMock, db)

            val parentTxn = Transaction(id = 1, description = "Test", amount = 100.0, date = 1000L, accountId = 1, categoryId = 1, notes = "Parent note", transactionType = "expense")
            val childTxn = Transaction(id = 2, description = "Child", amount = 50.0, date = 2000L, accountId = 1, categoryId = 2, notes = "Child note", transactionType = "expense", sourceSmsHash = "hash42")

            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(parentTxn)
            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(childTxn)

            repository.mergeTransactions(1, 2)

            val mergeCaptor = ArgumentCaptor.forClass(io.pm.finlight.data.db.entity.MergeRecord::class.java)
            verify(mergeRecordDaoMock).insert(mergeCaptor.capture() ?: io.pm.finlight.data.db.entity.MergeRecord(parentTxnId = 0, originalParentAmount = 0.0, originalParentDate = 0L, originalParentNotes = null, childDescription = "", childAmount = 0.0, childDate = 0L, childAccountId = 0, childCategoryId = null, childTransactionType = "", childSource = "", childNotes = null, childSourceSmsId = null, childSourceSmsHash = null, childSmsSignature = null, childOriginalDescription = null, childOriginalAmount = null, childCurrencyCode = null, childConversionRate = null))

            val captured = mergeCaptor.value
            assertEquals(1, captured.parentTxnId)
            assertEquals(100.0, captured.originalParentAmount, 0.001)
            assertEquals(1000L, captured.originalParentDate)
            assertEquals("Parent note", captured.originalParentNotes)
        }

    @Test
    fun `mergeTransactions inserts MergeRecord snapshot with correct child fields`() =
        runTest {
            setupDefaultPropertyMocks()
            val mergeRecordDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.MergeRecordDao::class.java)
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDaoMock, db)

            val parentTxn = Transaction(id = 1, description = "Test", amount = 100.0, date = 1000L, accountId = 1, categoryId = 1, notes = null, transactionType = "expense")
            val childTxn = Transaction(id = 2, description = "Swiggy", amount = 75.0, date = 3000L, accountId = 2, categoryId = 5, notes = "Lunch", transactionType = "expense", sourceSmsHash = "abc", smsSignature = "SBI", source = "sms")

            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(parentTxn)
            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(childTxn)

            repository.mergeTransactions(1, 2)

            val mergeCaptor = ArgumentCaptor.forClass(io.pm.finlight.data.db.entity.MergeRecord::class.java)
            verify(mergeRecordDaoMock).insert(mergeCaptor.capture() ?: io.pm.finlight.data.db.entity.MergeRecord(parentTxnId = 0, originalParentAmount = 0.0, originalParentDate = 0L, originalParentNotes = null, childDescription = "", childAmount = 0.0, childDate = 0L, childAccountId = 0, childCategoryId = null, childTransactionType = "", childSource = "", childNotes = null, childSourceSmsId = null, childSourceSmsHash = null, childSmsSignature = null, childOriginalDescription = null, childOriginalAmount = null, childCurrencyCode = null, childConversionRate = null))

            val captured = mergeCaptor.value
            assertEquals("Swiggy", captured.childDescription)
            assertEquals(75.0, captured.childAmount, 0.001)
            assertEquals(3000L, captured.childDate)
            assertEquals(2, captured.childAccountId)
            assertEquals(5, captured.childCategoryId)
            assertEquals("expense", captured.childTransactionType)
            assertEquals("sms", captured.childSource)
            assertEquals("Lunch", captured.childNotes)
            assertEquals("abc", captured.childSourceSmsHash)
            assertEquals("SBI", captured.childSmsSignature)
        }

    @Test
    fun `unmergeTransactions restores parent amount and date from snapshot`() =
        runTest {
            setupDefaultPropertyMocks()
            val mergeRecordDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.MergeRecordDao::class.java)
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDaoMock, db)

            val record =
                io.pm.finlight.data.db.entity.MergeRecord(
                    id = 1,
                    parentTxnId = 10,
                    originalParentAmount = 500.0,
                    originalParentDate = 99000L,
                    originalParentNotes = "original note",
                    childDescription = "Child",
                    childAmount = 250.0,
                    childDate = 100000L,
                    childAccountId = 1,
                    childCategoryId = 2,
                    childTransactionType = "expense",
                    childSource = "manual",
                    childNotes = null,
                    childSourceSmsId = null,
                    childSourceSmsHash = null,
                    childSmsSignature = null,
                    childOriginalDescription = null,
                    childOriginalAmount = null,
                    childCurrencyCode = null,
                    childConversionRate = null,
                )
            `when`(mergeRecordDaoMock.getForParentSync(10)).thenReturn(record)

            repository.unmergeTransactions(10)

            verify(transactionDao).updateAmount(10, 500.0)
            verify(transactionDao).updateDate(10, 99000L)
            verify(transactionDao).updateNotes(10, "original note")
        }

    @Test
    fun `unmergeTransactions reinserts child transaction with correct fields`() =
        runTest {
            setupDefaultPropertyMocks()
            val mergeRecordDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.MergeRecordDao::class.java)
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDaoMock, db)

            val record =
                io.pm.finlight.data.db.entity.MergeRecord(
                    id = 5,
                    parentTxnId = 10,
                    originalParentAmount = 100.0,
                    originalParentDate = 1000L,
                    originalParentNotes = null,
                    childDescription = "Swiggy",
                    childAmount = 200.0,
                    childDate = 5000L,
                    childAccountId = 3,
                    childCategoryId = 7,
                    childTransactionType = "expense",
                    childSource = "sms",
                    childNotes = "Dinner",
                    childSourceSmsId = 12345L,
                    childSourceSmsHash = "hashXYZ",
                    childSmsSignature = "HDFC",
                    childOriginalDescription = "SWIGGY",
                    childOriginalAmount = 200.0,
                    childCurrencyCode = null,
                    childConversionRate = null,
                )
            `when`(mergeRecordDaoMock.getForParentSync(10)).thenReturn(record)
            `when`(transactionDao.insert(anyObject())).thenReturn(99L)

            repository.unmergeTransactions(10)

            val txnCaptor = ArgumentCaptor.forClass(Transaction::class.java)
            verify(transactionDao).insert(txnCaptor.capture() ?: Transaction(description = "", amount = 0.0, date = 0L, accountId = 0, categoryId = 0, transactionType = "", notes = null))

            val inserted = txnCaptor.value
            assertEquals("Swiggy", inserted.description)
            assertEquals(200.0, inserted.amount, 0.001)
            assertEquals(5000L, inserted.date)
            assertEquals(3, inserted.accountId)
            assertEquals(7, inserted.categoryId)
            assertEquals("expense", inserted.transactionType)
            assertEquals("sms", inserted.source)
            assertEquals("Dinner", inserted.notes)
            assertEquals(12345L, inserted.sourceSmsId)
            assertEquals("hashXYZ", inserted.sourceSmsHash)
            assertEquals(false, inserted.mergeDismissed)
        }

    @Test
    fun `unmergeTransactions removes SMS hash from deny-list when present`() =
        runTest {
            setupDefaultPropertyMocks()
            val mergeRecordDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.MergeRecordDao::class.java)
            val deletedSmsHashDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.DeletedSmsHashDao::class.java)
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDaoMock, mergeRecordDaoMock, db)

            val record =
                io.pm.finlight.data.db.entity.MergeRecord(
                    id = 3,
                    parentTxnId = 10,
                    originalParentAmount = 100.0,
                    originalParentDate = 1000L,
                    originalParentNotes = null,
                    childDescription = "Child",
                    childAmount = 50.0,
                    childDate = 2000L,
                    childAccountId = 1,
                    childCategoryId = 1,
                    childTransactionType = "expense",
                    childSource = "sms",
                    childNotes = null,
                    childSourceSmsId = null,
                    childSourceSmsHash = "smsHash99",
                    childSmsSignature = null,
                    childOriginalDescription = null,
                    childOriginalAmount = null,
                    childCurrencyCode = null,
                    childConversionRate = null,
                )
            `when`(mergeRecordDaoMock.getForParentSync(10)).thenReturn(record)
            `when`(transactionDao.insert(anyObject())).thenReturn(1L)

            repository.unmergeTransactions(10)

            verify(deletedSmsHashDaoMock).deleteByHash("smsHash99")
        }

    @Test
    fun `unmergeTransactions does not remove SMS hash when child has no hash`() =
        runTest {
            setupDefaultPropertyMocks()
            val mergeRecordDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.MergeRecordDao::class.java)
            val deletedSmsHashDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.DeletedSmsHashDao::class.java)
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDaoMock, mergeRecordDaoMock, db)

            val record =
                io.pm.finlight.data.db.entity.MergeRecord(
                    id = 3,
                    parentTxnId = 10,
                    originalParentAmount = 100.0,
                    originalParentDate = 1000L,
                    originalParentNotes = null,
                    childDescription = "Child",
                    childAmount = 50.0,
                    childDate = 2000L,
                    childAccountId = 1,
                    childCategoryId = 1,
                    childTransactionType = "expense",
                    childSource = "manual",
                    childNotes = null,
                    childSourceSmsId = null,
                    childSourceSmsHash = null,
                    childSmsSignature = null,
                    childOriginalDescription = null,
                    childOriginalAmount = null,
                    childCurrencyCode = null,
                    childConversionRate = null,
                )
            `when`(mergeRecordDaoMock.getForParentSync(10)).thenReturn(record)
            `when`(transactionDao.insert(anyObject())).thenReturn(1L)

            repository.unmergeTransactions(10)

            verify(deletedSmsHashDaoMock, org.mockito.Mockito.never()).deleteByHash(org.mockito.kotlin.any())
        }

    @Test
    fun `unmergeTransactions deletes MergeRecord after successful unmerge`() =
        runTest {
            setupDefaultPropertyMocks()
            val mergeRecordDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.MergeRecordDao::class.java)
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDaoMock, db)

            val record =
                io.pm.finlight.data.db.entity.MergeRecord(
                    id = 42,
                    parentTxnId = 10,
                    originalParentAmount = 100.0,
                    originalParentDate = 1000L,
                    originalParentNotes = null,
                    childDescription = "Child",
                    childAmount = 50.0,
                    childDate = 2000L,
                    childAccountId = 1,
                    childCategoryId = 1,
                    childTransactionType = "expense",
                    childSource = "manual",
                    childNotes = null,
                    childSourceSmsId = null,
                    childSourceSmsHash = null,
                    childSmsSignature = null,
                    childOriginalDescription = null,
                    childOriginalAmount = null,
                    childCurrencyCode = null,
                    childConversionRate = null,
                )
            `when`(mergeRecordDaoMock.getForParentSync(10)).thenReturn(record)
            `when`(transactionDao.insert(anyObject())).thenReturn(1L)

            repository.unmergeTransactions(10)

            verify(mergeRecordDaoMock).deleteById(42)
        }

    @Test
    fun `unmergeTransactions is no-op when no MergeRecord exists`() =
        runTest {
            setupDefaultPropertyMocks()
            val mergeRecordDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.MergeRecordDao::class.java)
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDaoMock, db)

            `when`(mergeRecordDaoMock.getForParentSync(99)).thenReturn(null)

            repository.unmergeTransactions(99)

            // No DAO mutations should be called
            verify(transactionDao, org.mockito.Mockito.never()).updateAmount(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyDouble())
            verify(transactionDao, org.mockito.Mockito.never()).insert(anyObject())
            verify(mergeRecordDaoMock, org.mockito.Mockito.never()).deleteById(org.mockito.ArgumentMatchers.anyInt())
        }

    @Test
    fun `observeMergeRecord delegates to MergeRecordDao`() =
        runTest {
            setupDefaultPropertyMocks()
            val mergeRecordDaoMock = org.mockito.Mockito.mock(io.pm.finlight.data.db.dao.MergeRecordDao::class.java)
            repository = TransactionRepository(transactionDao, settingsRepository, tagRepository, deletedSmsHashDao, mergeRecordDaoMock, db)

            `when`(mergeRecordDaoMock.observeForParent(7)).thenReturn(flowOf(null))

            repository.observeMergeRecord(7).test {
                assertEquals(null, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(mergeRecordDaoMock).observeForParent(7)
        }
}

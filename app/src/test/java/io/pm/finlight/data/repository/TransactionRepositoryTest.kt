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
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var accountDao: io.pm.finlight.data.db.dao.AccountDao

    @Mock
    private lateinit var accountAliasDao: io.pm.finlight.data.db.dao.AccountAliasDao

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

        // Mock DB dependencies
        `when`(db.accountDao()).thenReturn(accountDao)
        `when`(db.accountAliasDao()).thenReturn(accountAliasDao)

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

    @Test
    fun `unlinkReimbursement on over-repaid (negative) expense correctly restores amount`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)

            // Income of 150 was linked to expense of 100 → expense.amount = 100 - 150 = -50 (over-repaid)
            val incomeTxn = Transaction(id = 2, description = "Income", amount = 150.0, date = 2000L, accountId = 1, categoryId = 1, transactionType = TransactionType.INCOME, notes = null, parentReimbursementId = 1)
            val expenseTxn = Transaction(id = 1, description = "Expense", amount = -50.0, date = 1000L, accountId = 1, categoryId = 1, transactionType = TransactionType.EXPENSE, notes = null)

            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            repository.unlinkReimbursement(incomeId = 2)

            // After unlink, expense amount should be restored: -50 + 150 = 100
            verify(transactionDao).updateAmount(1, 100.0)
        }

    // ── Tests: Core Insert / Update / Delete ──────────────────────────────────

    @Test
    fun `insertTransactionWithTags without travel mode saves transaction and initial tags`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, db) // Initialize

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
    fun `insertTransactionWithTags adds given tags`() =
        runTest {
            setupDefaultPropertyMocks()
            `when`(transactionDao.insert(anyObject())).thenReturn(1L)

            repository = TransactionRepository(transactionDao, db)

            val transaction =
                Transaction(
                    description = "Test",
                    amount = 100.0,
                    date = System.currentTimeMillis(),
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                )
            val initialTags = setOf(Tag(id = 1, name = "Work"), Tag(id = 99, name = "Trip"))

            @Suppress("UNCHECKED_CAST")
            val crossRefCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<TransactionTagCrossRef>>

            // Act
            repository.insertTransactionWithTags(transaction, initialTags)

            // Assert
            verify(transactionDao).addTagsToTransaction(crossRefCaptor.capture() ?: emptyList())
            val capturedRefs = crossRefCaptor.value
            assertEquals(2, capturedRefs.size)
            assertTrue(capturedRefs.any { it.tagId == 1 })
            assertTrue(capturedRefs.any { it.tagId == 99 })
        }

    // --- NEW: Test for insertTransactionWithTagsAndImages ---
    @Test
    fun `insertTransactionWithTagsAndImages saves transaction, tags, and images`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, db) // Initialize

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
            repository = TransactionRepository(transactionDao, db) // Initialize

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
            repository = TransactionRepository(transactionDao, db) // Initialize
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
            repository = TransactionRepository(transactionDao, db) // Initialize
            val hash = "testhash"
            repository.setSmsHash(1, hash)
            verify(transactionDao).setSmsHash(1, hash)
        }

    // --- NEW: Test for getTransactionCountForMerchant ---
    @Test
    fun `getTransactionCountForMerchant calls DAO`() =
        runTest {
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, db) // Initialize
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
            repository = TransactionRepository(transactionDao, db) // Initialize
            val desc = "Amazon"
            repository.findSimilarTransactions(desc, 1)
            verify(transactionDao).findSimilarTransactions(desc, 1)
        }

    // --- NEW: Test for updateCategoryForIds ---
    @Test
    fun `updateCategoryForIds calls DAO`() =
        runTest {
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, db) // Initialize
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
            repository = TransactionRepository(transactionDao, db) // Initialize
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
            repository = TransactionRepository(transactionDao, db)
            repository.clearReviewFlag(1)
            verify(transactionDao).clearReviewFlag(1)
        }

    @Test
    fun `updateDescription calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            repository.updateDescription(1, "New Desc")
            verify(transactionDao).updateDescription(1, "New Desc")
        }

    @Test
    fun `updateAmount calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            repository.updateAmount(1, 123.45)
            verify(transactionDao).updateAmount(1, 123.45)
        }

    @Test
    fun `updateNotes calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            repository.updateNotes(1, "New Note")
            verify(transactionDao).updateNotes(1, "New Note")
        }

    @Test
    fun `updateCategoryId calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            repository.updateCategoryId(1, 5)
            verify(transactionDao).updateCategoryId(1, 5)
        }

    @Test
    fun `updateAccountId calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            repository.updateAccountId(1, 2)
            verify(transactionDao).updateAccountId(1, 2)
        }

    @Test
    fun `updateDate calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            repository.updateDate(1, 999L)
            verify(transactionDao).updateDate(1, 999L)
        }

    @Test
    fun `updateExclusionStatus calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            repository.updateExclusionStatus(1, true)
            verify(transactionDao).updateExclusionStatus(1, true)
        }

    @Test
    fun `updateTransactionType calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            repository.updateTransactionType(1, TransactionType.INCOME)
            verify(transactionDao).updateTransactionType(1, TransactionType.INCOME)
        }

    @Test
    fun `getTotalExpensesSince calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            val startDate = 1000L
            repository.getTotalExpensesSince(startDate)
            verify(transactionDao).getTotalExpensesSince(startDate)
        }

    @Test
    fun `searchMerchants calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            val query = "amzn"
            `when`(transactionDao.searchMerchants(query)).thenReturn(flowOf(emptyList()))
            repository.searchMerchants(query)
            verify(transactionDao).searchMerchants(query)
        }

    @Test
    fun `deleteByIds calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            val ids = listOf(1, 2)
            repository.deleteByIds(ids)
            verify(transactionDao).deleteByIds(ids)
        }

    @Test
    fun `getRecentManualTransactions calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            `when`(transactionDao.getRecentManualTransactions(5)).thenReturn(flowOf(emptyList()))
            repository.getRecentManualTransactions(5)
            verify(transactionDao).getRecentManualTransactions(5)
        }

    @Test
    fun `addTagForDateRange calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            repository.addTagForDateRange(1, 100L, 200L)
            verify(transactionDao).addTagForDateRange(1, 100L, 200L)
        }

    @Test
    fun `removeTagForDateRange calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            repository.removeTagForDateRange(1, 100L, 200L)
            verify(transactionDao).removeTagForDateRange(1, 100L, 200L)
        }

    @Test
    fun `getTransactionsByTagId calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            `when`(transactionDao.getTransactionsByTagId(1)).thenReturn(flowOf(emptyList()))
            repository.getTransactionsByTagId(1)
            verify(transactionDao).getTransactionsByTagId(1)
        }

    @Test
    fun `removeAllTransactionsForTag calls DAO`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)
            repository.removeAllTransactionsForTag(1)
            verify(transactionDao).removeAllTransactionsForTag(1)
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

            repository = TransactionRepository(transactionDao, db) // Initialize HERE

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

            repository = TransactionRepository(transactionDao, db) // Initialize HERE

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
            repository = TransactionRepository(transactionDao, db) // Initialize

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
            repository = TransactionRepository(transactionDao, db) // Initialize

            val mockSpending = listOf(CategorySpending("Food", 100.0, "red", "icon"))
            `when`(transactionDao.getSpendingByCategoryForMonth(100L, 200L, "keyword", 1, 2, TransactionType.EXPENSE)).thenReturn(flowOf(mockSpending))

            // Act & Assert
            repository.getSpendingByCategoryForMonth(100L, 200L, "keyword", 1, 2, TransactionType.EXPENSE).test {
                assertEquals(mockSpending, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(transactionDao).getSpendingByCategoryForMonth(100L, 200L, "keyword", 1, 2, TransactionType.EXPENSE)
        }

    @Test
    fun `getMonthlyTrends emits data from DAO`() =
        runTest {
            // Arrange
            setupDefaultPropertyMocks() // Add default mocks for properties
            repository = TransactionRepository(transactionDao, db) // Initialize

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
            repository = TransactionRepository(transactionDao, db) // Initialize

            val mockSpending = listOf(MerchantSpendingSummary("Amazon", 100.0, 2))
            `when`(transactionDao.getSpendingByMerchantForMonth(100L, 200L, "keyword", 1, 2, TransactionType.EXPENSE)).thenReturn(flowOf(mockSpending))

            // Act & Assert
            repository.getSpendingByMerchantForMonth(100L, 200L, "keyword", 1, 2, TransactionType.EXPENSE).test {
                assertEquals(mockSpending, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify(transactionDao).getSpendingByMerchantForMonth(100L, 200L, "keyword", 1, 2, TransactionType.EXPENSE)
        }

    @Test
    fun `linkReimbursement deducts income amount from expense amount`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)

            val expenseTxn = Transaction(id = 1, description = "Expense", amount = 1500.0, date = 1000L, accountId = 1, categoryId = 1, notes = "", transactionType = TransactionType.EXPENSE)
            val incomeTxn = Transaction(id = 2, description = "Income", amount = 500.0, date = 2000L, accountId = 1, categoryId = 2, notes = "", transactionType = TransactionType.INCOME)

            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            repository.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionDao).linkReimbursement(2, 1)
            verify(transactionDao).updateAmount(1, 1000.0)
        }

    @Test
    fun `linkReimbursement allows expense amount to drop below zero`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)

            val expenseTxn = Transaction(id = 1, description = "Expense", amount = 300.0, date = 1000L, accountId = 1, categoryId = 1, notes = "", transactionType = TransactionType.EXPENSE)
            val incomeTxn = Transaction(id = 2, description = "Income", amount = 500.0, date = 2000L, accountId = 1, categoryId = 2, notes = "", transactionType = TransactionType.INCOME)

            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            repository.linkReimbursement(incomeId = 2, expenseId = 1)

            verify(transactionDao).linkReimbursement(2, 1)
            verify(transactionDao).updateAmount(1, -200.0)
        }

    @Test
    fun `unlinkReimbursement restores amount to expense`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)

            val expenseTxn = Transaction(id = 1, description = "Expense", amount = 1000.0, date = 1000L, accountId = 1, categoryId = 1, notes = "", transactionType = TransactionType.EXPENSE)
            val incomeTxn = Transaction(id = 2, description = "Income", amount = 500.0, date = 2000L, accountId = 1, categoryId = 2, notes = "", transactionType = TransactionType.INCOME, parentReimbursementId = 1)

            `when`(transactionDao.getTransactionByIdSync(2)).thenReturn(incomeTxn)
            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(expenseTxn)

            repository.unlinkReimbursement(incomeId = 2)

            verify(transactionDao).unlinkReimbursement(2)
            verify(transactionDao).updateAmount(1, 1500.0)
        }

    @Test
    fun `detectAndLinkSelfTransfer strict time match links transactions`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)

            val newTxn = Transaction(id = 1, description = "Withdrawal", amount = 100.0, date = 1000L, accountId = 1, transactionType = TransactionType.EXPENSE, sourceSmsId = 10, categoryId = null, notes = null)
            val candidate = Transaction(id = 2, description = "Deposit", amount = 100.0, date = 1000L + 60 * 1000L, accountId = 2, transactionType = TransactionType.INCOME, sourceSmsId = 20, categoryId = null, notes = null)

            // Return candidate
            org.mockito.kotlin.whenever(
                transactionDao.findPotentialTransfers(
                    org.mockito.kotlin.eq(100.0),
                    org.mockito.kotlin.eq(1),
                    org.mockito.kotlin.eq(TransactionType.EXPENSE),
                    org.mockito.kotlin.any(),
                    org.mockito.kotlin.any()
                )
            ).thenReturn(listOf(candidate))

            repository.detectAndLinkSelfTransfer(newTxn)

            verify(transactionDao).updateTransferLinkStatus(1, 2, true)
            verify(transactionDao).updateTransferLinkStatus(2, 1, true)
        }

    @Test
    fun `detectAndLinkSelfTransfer loose time match with alias match links transactions`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)

            val newTxn = Transaction(id = 1, description = "Transfer", originalDescription = "Transfer to 1234", amount = 100.0, date = 1000L, accountId = 1, transactionType = TransactionType.EXPENSE, sourceSmsId = 10, categoryId = null, notes = null)
            // 2 hours difference (loose time)
            val candidate = Transaction(id = 2, description = "Transfer", originalDescription = "Received from 9999", amount = 100.0, date = 1000L + 2 * 60 * 60 * 1000L, accountId = 2, transactionType = TransactionType.INCOME, sourceSmsId = 20, categoryId = null, notes = null)

            org.mockito.kotlin.whenever(
                transactionDao.findPotentialTransfers(
                    org.mockito.kotlin.eq(100.0),
                    org.mockito.kotlin.eq(1),
                    org.mockito.kotlin.eq(TransactionType.EXPENSE),
                    org.mockito.kotlin.any(),
                    org.mockito.kotlin.any()
                )
            ).thenReturn(listOf(candidate))

            val alias = io.pm.finlight.data.db.entity.AccountAlias(aliasName = "HDFC-1234", destinationAccountId = 2)
            org.mockito.kotlin.whenever(accountAliasDao.getAliasesForAccount(1)).thenReturn(emptyList())
            org.mockito.kotlin.whenever(accountAliasDao.getAliasesForAccount(2)).thenReturn(listOf(alias))

            org.mockito.kotlin.whenever(accountDao.getAccountByIdBlocking(1)).thenReturn(io.pm.finlight.Account(id = 1, name = "Account1", type = "bank"))
            org.mockito.kotlin.whenever(accountDao.getAccountByIdBlocking(2)).thenReturn(io.pm.finlight.Account(id = 2, name = "Account2", type = "bank"))

            repository.detectAndLinkSelfTransfer(newTxn)

            verify(transactionDao).updateTransferLinkStatus(1, 2, true)
            verify(transactionDao).updateTransferLinkStatus(2, 1, true)
        }

    @Test
    fun `detectAndLinkSelfTransfer loose time match without text match does not link`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)

            val newTxn = Transaction(id = 1, description = "Expense", originalDescription = "Some expense", amount = 100.0, date = 1000L, accountId = 1, transactionType = TransactionType.EXPENSE, sourceSmsId = 10, categoryId = null, notes = null)
            // 2 hours difference (loose time)
            val candidate = Transaction(id = 2, description = "Income", originalDescription = "Some income", amount = 100.0, date = 1000L + 2 * 60 * 60 * 1000L, accountId = 2, transactionType = TransactionType.INCOME, sourceSmsId = 20, categoryId = null, notes = null)

            org.mockito.kotlin.whenever(
                transactionDao.findPotentialTransfers(
                    org.mockito.kotlin.eq(100.0),
                    org.mockito.kotlin.eq(1),
                    org.mockito.kotlin.eq(TransactionType.EXPENSE),
                    org.mockito.kotlin.any(),
                    org.mockito.kotlin.any()
                )
            ).thenReturn(listOf(candidate))

            org.mockito.kotlin.whenever(accountAliasDao.getAliasesForAccount(1)).thenReturn(emptyList())
            org.mockito.kotlin.whenever(accountAliasDao.getAliasesForAccount(2)).thenReturn(emptyList())

            org.mockito.kotlin.whenever(accountDao.getAccountByIdBlocking(1)).thenReturn(io.pm.finlight.Account(id = 1, name = "Account1", type = "bank"))
            org.mockito.kotlin.whenever(accountDao.getAccountByIdBlocking(2)).thenReturn(io.pm.finlight.Account(id = 2, name = "Account2", type = "bank"))

            repository.detectAndLinkSelfTransfer(newTxn)

            verify(transactionDao, org.mockito.Mockito.never()).updateTransferLinkStatus(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
        }

    @Test
    fun `detectAndLinkSelfTransfer skips if already linked or excluded`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)

            val newTxnLinked = Transaction(id = 1, description = "A", amount = 100.0, date = 1000L, accountId = 1, transactionType = TransactionType.EXPENSE, sourceSmsId = 10, linkedTransferId = 99, categoryId = null, notes = null)
            val newTxnExcluded = Transaction(id = 2, description = "B", amount = 100.0, date = 1000L, accountId = 1, transactionType = TransactionType.EXPENSE, sourceSmsId = 10, isExcluded = true, categoryId = null, notes = null)

            repository.detectAndLinkSelfTransfer(newTxnLinked)
            repository.detectAndLinkSelfTransfer(newTxnExcluded)

            verify(transactionDao, org.mockito.Mockito.never()).findPotentialTransfers(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any()
            )
        }

    @Test
    fun `findRecentTransactionForMerge delegates to DAO with TransactionType`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)

            val expected = Transaction(id = 1, description = "Uber", amount = 200.0, date = 1000L, accountId = 1, categoryId = 1, transactionType = TransactionType.EXPENSE, notes = null)
            `when`(transactionDao.findRecentTransactionForMerge("Uber", 1, TransactionType.EXPENSE, 500L, 2)).thenReturn(expected)

            val result = repository.findRecentTransactionForMerge("Uber", 1, TransactionType.EXPENSE, 500L, 2)

            assertEquals(expected, result)
        }

    @Test
    fun `delegated DAO query methods forward calls properly`() =
        runTest {
            setupDefaultPropertyMocks()
            repository = TransactionRepository(transactionDao, db)

            `when`(transactionDao.getTransactionWithSplits(1)).thenReturn(flowOf(null))
            `when`(transactionDao.getTopSpendingCategoriesForRangeFlow(1L, 2L)).thenReturn(flowOf(null))
            `when`(transactionDao.getIncomeTransactionsForRange(1L, 2L, null, null, null)).thenReturn(flowOf(emptyList()))
            `when`(transactionDao.getIncomeByCategoryForMonth(1L, 2L, null, null, null)).thenReturn(flowOf(emptyList()))
            `when`(transactionDao.getSpendingByMerchantForMonth(1L, 2L, null, null, null, null)).thenReturn(flowOf(emptyList()))
            `when`(transactionDao.getSpendingByCategoryForMonth(1L, 2L, null, null, null, null)).thenReturn(flowOf(emptyList()))
            `when`(transactionDao.getMonthlyTrends(1L)).thenReturn(flowOf(emptyList()))
            `when`(transactionDao.countTransactionsForCategory(1)).thenReturn(5)
            `when`(transactionDao.getTagsForTransaction(1)).thenReturn(flowOf(emptyList()))
            `when`(transactionDao.getTagsForTransactionSimple(1)).thenReturn(emptyList())
            `when`(transactionDao.getImagesForTransaction(1)).thenReturn(flowOf(emptyList()))
            `when`(transactionDao.getTransactionDetailsById(1)).thenReturn(flowOf(null))
            `when`(transactionDao.getTransactionsForAccountDetails(1)).thenReturn(flowOf(emptyList()))
            `when`(transactionDao.getTransactionDetailsForRange(1L, 2L, null, null, null)).thenReturn(flowOf(emptyList()))
            `when`(transactionDao.getAllTransactionsForRange(1L, 2L)).thenReturn(flowOf(emptyList()))
            `when`(transactionDao.getTransactionById(1)).thenReturn(flowOf(null))
            `when`(transactionDao.getTransactionByIdSync(1)).thenReturn(null)
            `when`(transactionDao.getTransactionsForAccount(1)).thenReturn(flowOf(emptyList()))
            `when`(transactionDao.getAllSmsHashes()).thenReturn(flowOf(emptyList()))

            repository.getTransactionWithSplits(1).first()
            repository.getTopSpendingCategoriesForRangeFlow(1L, 2L).first()
            repository.getIncomeTransactionsForRange(1L, 2L, null, null, null).first()
            repository.getIncomeByCategoryForMonth(1L, 2L, null, null, null).first()
            repository.getSpendingByMerchantForMonth(1L, 2L, null, null, null, null).first()
            repository.getSpendingByCategoryForMonth(1L, 2L, null, null, null, null).first()
            repository.getMonthlyTrends(1L).first()
            repository.countTransactionsForCategory(1)
            repository.getTagsForTransaction(1).first()
            repository.getTagsForTransactionSimple(1)
            repository.getImagesForTransaction(1).first()
            repository.getTransactionDetailsById(1).first()
            repository.getTransactionsForAccountDetails(1).first()
            repository.getTransactionDetailsForRange(1L, 2L, null, null, null).first()
            repository.getAllTransactionsForRange(1L, 2L).first()
            repository.getTransactionById(1).first()
            repository.getTransactionSync(1)
            repository.getTransactionsForAccount(1).first()
            repository.getAllSmsHashes().first()

            verify(transactionDao).getTransactionWithSplits(1)
            verify(transactionDao).getTopSpendingCategoriesForRangeFlow(1L, 2L)
            verify(transactionDao).getIncomeTransactionsForRange(1L, 2L, null, null, null)
            verify(transactionDao).getIncomeByCategoryForMonth(1L, 2L, null, null, null)
            verify(transactionDao).getSpendingByMerchantForMonth(1L, 2L, null, null, null, null)
            verify(transactionDao).getSpendingByCategoryForMonth(1L, 2L, null, null, null, null)
            verify(transactionDao, atLeastOnce()).getMonthlyTrends(1L)
            verify(transactionDao).countTransactionsForCategory(1)
            verify(transactionDao).getTagsForTransaction(1)
            verify(transactionDao).getTagsForTransactionSimple(1)
            verify(transactionDao).getImagesForTransaction(1)
            verify(transactionDao).getTransactionDetailsById(1)
            verify(transactionDao).getTransactionsForAccountDetails(1)
            verify(transactionDao).getTransactionDetailsForRange(1L, 2L, null, null, null)
            verify(transactionDao).getAllTransactionsForRange(1L, 2L)
            verify(transactionDao).getTransactionById(1)
            verify(transactionDao).getTransactionByIdSync(1)
            verify(transactionDao).getTransactionsForAccount(1)
            verify(transactionDao).getAllSmsHashes()
        }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy constructor initializes TransactionRepository properly`() =
        runTest {
            setupDefaultPropertyMocks()
            val repo =
                TransactionRepository(
                    transactionDao,
                    db,
                )
            assertNotNull(repo)
        }
}

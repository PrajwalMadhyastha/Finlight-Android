package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.util.DatabaseTestRule
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.junit.JUnitAsserter.assertNotNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var tagDao: TagDao
    private lateinit var splitTransactionDao: SplitTransactionDao // --- ADDED ---

    // Test data
    private val account1 = Account(id = 1, name = "Savings", type = "Bank")
    private val account2 = Account(id = 2, name = "Credit Card", type = "Card")
    private val category1 = Category(id = 1, name = "Food", iconKey = "restaurant", colorKey = "red_light")
    private val category2 = Category(id = 2, name = "Travel", iconKey = "travel", colorKey = "blue_light")
    private val category3 = Category(id = 3, name = "Salary", iconKey = "work", colorKey = "green_light")

    private val tag1 = Tag(id = 1, name = "Work")
    private val tag2 = Tag(id = 2, name = "Vacation")

    @Before
    fun setup() = runTest {
        transactionDao = dbRule.db.transactionDao()
        accountDao = dbRule.db.accountDao()
        categoryDao = dbRule.db.categoryDao()
        tagDao = dbRule.db.tagDao()
        splitTransactionDao = dbRule.db.splitTransactionDao() // --- ADDED ---

        // Pre-populate database with necessary entities
        accountDao.insertAll(listOf(account1, account2))
        categoryDao.insertAll(listOf(category1, category2, category3))
        tagDao.insertAll(listOf(tag1, tag2))
    }

    // --- NEW TEST CASE TO VERIFY THE FIX ---
    @Test
    fun `getDailySpendingForDateRange correctly groups by day and sums amounts`() = runTest {
        // Arrange
        val cal = Calendar.getInstance()
        val todayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val todayEnd = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis

        val yesterday = cal.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis

        // 1. Regular expense (100.0)
        transactionDao.insert(Transaction(description = "Coffee", amount = 100.0, date = todayStart + 1000, transactionType = "expense", accountId = 1, categoryId = 1, notes = null))

        // 2. Another regular expense (200.0)
        transactionDao.insert(Transaction(description = "Lunch", amount = 200.0, date = todayStart + 2000, transactionType = "expense", accountId = 1, categoryId = 1, notes = null))

        // 3. Excluded expense (50.0) - SHOULD BE IGNORED
        transactionDao.insert(Transaction(description = "Excluded", amount = 50.0, date = todayStart + 3000, transactionType = "expense", accountId = 1, categoryId = 1, notes = null, isExcluded = true))

        // 4. Income (1000.0) - SHOULD BE IGNORED
        transactionDao.insert(Transaction(description = "Salary", amount = 1000.0, date = todayStart + 4000, transactionType = "income", accountId = 1, categoryId = 3, notes = null))

        // 5. Transaction from another day - SHOULD BE IGNORED
        transactionDao.insert(Transaction(description = "Yesterday", amount = 75.0, date = yesterday, transactionType = "expense", accountId = 1, categoryId = 1, notes = null))

        // 6. Split transaction (300.0 + 400.0 = 700.0)
        val parentId = transactionDao.insert(Transaction(description = "Split", amount = 700.0, date = todayStart + 5000, transactionType = "expense", accountId = 1, categoryId = null, notes = null, isSplit = true)).toInt()
        splitTransactionDao.insertAll(listOf(
            SplitTransaction(parentTransactionId = parentId, amount = 300.0, categoryId = 1, notes = null),
            SplitTransaction(parentTransactionId = parentId, amount = 400.0, categoryId = 2, notes = null)
        ))

        // Expected total for today: 100.0 + 200.0 + 300.0 + 400.0 = 1000.0

        // Act & Assert
        transactionDao.getDailySpendingForDateRange(todayStart, todayEnd).test {
            val dailyTotals = awaitItem()

            // Assert: We should have exactly one record for today.
            assertEquals(1, dailyTotals.size)

            val todayTotal = dailyTotals.first()
            assertEquals("Total amount should be the sum of all non-excluded expenses (regular + split)", 1000.0, todayTotal.totalAmount, 0.01)

            val expectedDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(todayStart))
            assertEquals("The date key should be the formatted date string", expectedDateKey, todayTotal.date)

            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `getTransactionsForMerchantInRange is case-insensitive`() = runTest {
        // Arrange
        val transactionTime = System.currentTimeMillis()
        val transaction = Transaction(
            id = 1,
            description = "Amazon", // Original-cased merchant name
            amount = 500.0,
            date = transactionTime,
            accountId = 1,
            categoryId = 2,
            notes = null
        )
        transactionDao.insert(transaction)

        // Act & Assert
        // Query with a lowercase version of the merchant name
        transactionDao.getTransactionsForMerchantInRange("amazon", transactionTime - 1000, transactionTime + 1000).test {
            val results = awaitItem()

            // The query should still find the transaction, proving the fix works.
            assertEquals(1, results.size)
            assertEquals("Amazon", results.first().transaction.description)

            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `insert and get transaction by id`() = runTest {
        // Arrange
        val transaction = Transaction(id = 1, description = "Coffee", amount = 150.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = "Morning coffee")

        // Act
        transactionDao.insert(transaction)

        // Assert
        transactionDao.getTransactionById(1).test {
            val fromDb = awaitItem()
            assertNotNull(fromDb)
            assertEquals("Coffee", fromDb?.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTransactionDetailsForRange returns correctly joined data`() = runTest {
        // Arrange
        val transactionTime = System.currentTimeMillis()
        val transaction = Transaction(id = 1, description = "Flight Ticket", amount = 12000.0, date = transactionTime, accountId = 2, categoryId = 2, notes = "Vacation flight")
        transactionDao.insert(transaction)
        transactionDao.addTagsToTransaction(listOf(TransactionTagCrossRef(transactionId = 1, tagId = 2)))

        val startOfDay = transactionTime - 10000
        val endOfDay = transactionTime + 10000

        // Act & Assert
        transactionDao.getTransactionDetailsForRange(startOfDay, endOfDay, null, null, null).test {
            val detailsList = awaitItem()
            assertEquals(1, detailsList.size)
            val details = detailsList.first()

            assertEquals("Flight Ticket", details.transaction.description)
            assertEquals("Credit Card", details.accountName)
            assertEquals("Travel", details.categoryName)
            assertEquals("Vacation", details.tagNames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Test cases for specific update methods ---

    @Test
    fun `updateDescription correctly updates description`() = runTest {
        val id = transactionDao.insert(Transaction(description = "Old", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null)).toInt()
        val newDescription = "New Description"
        transactionDao.updateDescription(id, newDescription)
        transactionDao.getTransactionById(id).test {
            assertEquals(newDescription, awaitItem()?.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateAmount correctly updates amount`() = runTest {
        val id = transactionDao.insert(Transaction(description = "Test", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null)).toInt()
        val newAmount = 123.45
        transactionDao.updateAmount(id, newAmount)
        transactionDao.getTransactionById(id).test {
            assertEquals(newAmount, awaitItem()?.amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateNotes correctly updates notes`() = runTest {
        val id = transactionDao.insert(Transaction(description = "Test", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = "Old Note")).toInt()
        val newNotes = "New Note"
        transactionDao.updateNotes(id, newNotes)
        transactionDao.getTransactionById(id).test {
            assertEquals(newNotes, awaitItem()?.notes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateCategoryId correctly updates categoryId`() = runTest {
        val id = transactionDao.insert(Transaction(description = "Test", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null)).toInt()
        val newCategoryId = 2
        transactionDao.updateCategoryId(id, newCategoryId)
        transactionDao.getTransactionById(id).test {
            assertEquals(newCategoryId, awaitItem()?.categoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateAccountId correctly updates accountId`() = runTest {
        val id = transactionDao.insert(Transaction(description = "Test", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null)).toInt()
        val newAccountId = 2
        transactionDao.updateAccountId(id, newAccountId)
        transactionDao.getTransactionById(id).test {
            assertEquals(newAccountId, awaitItem()?.accountId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateDate correctly updates date`() = runTest {
        val id = transactionDao.insert(Transaction(description = "Test", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null)).toInt()
        val newDate = 9999L
        transactionDao.updateDate(id, newDate)
        transactionDao.getTransactionById(id).test {
            assertEquals(newDate, awaitItem()?.date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateExclusionStatus correctly updates isExcluded`() = runTest {
        val id = transactionDao.insert(Transaction(description = "Test", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null, isExcluded = false)).toInt()
        transactionDao.updateExclusionStatus(id, true)
        transactionDao.getTransactionById(id).test {
            assertEquals(true, awaitItem()?.isExcluded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateTransactionType correctly updates transactionType`() = runTest {
        val id = transactionDao.insert(Transaction(description = "Test", amount = 1.0, date = 1L, accountId = 1, categoryId = 1, notes = null, transactionType = "expense")).toInt()
        val newType = "income"
        transactionDao.updateTransactionType(id, newType)
        transactionDao.getTransactionById(id).test {
            assertEquals(newType, awaitItem()?.transactionType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Test cases for search permutations ---

    @Test
    fun `searchTransactions by keyword and accountId`() = runTest {
        // Arrange
        transactionDao.insert(Transaction(id = 1, description = "Coffee at Cafe", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(id = 2, description = "Coffee at Starbucks", amount = 200.0, date = System.currentTimeMillis(), accountId = 2, categoryId = 1, notes = null))

        // Act & Assert
        transactionDao.searchTransactions(keyword = "Coffee", accountId = 1, null, null, null, null, null).test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("Coffee at Cafe", results.first().transaction.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchTransactions by categoryId and transactionType`() = runTest {
        // Arrange
        transactionDao.insert(Transaction(id = 1, description = "Lunch", amount = 500.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, transactionType = "expense", notes = null))
        transactionDao.insert(Transaction(id = 2, description = "Salary", amount = 50000.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 3, transactionType = "income", notes = null))
        transactionDao.insert(Transaction(id = 3, description = "Bonus", amount = 5000.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 3, transactionType = "income", notes = null))

        // Act & Assert
        transactionDao.searchTransactions(keyword = "", null, categoryId = 3, transactionType = "income", null, null, null).test {
            val results = awaitItem()
            assertEquals(2, results.size)
            assertTrue(results.all { it.transaction.transactionType == "income" && it.transaction.categoryId == 3 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchTransactions by dateRange and tagId`() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val yesterday = now - 86400000
        val tx1Id = transactionDao.insert(Transaction(id = 1, description = "Work Lunch", amount = 300.0, date = now, accountId = 1, categoryId = 1, notes = null)).toInt()
        val tx2Id = transactionDao.insert(Transaction(id = 2, description = "Vacation Flight", amount = 8000.0, date = yesterday, accountId = 1, categoryId = 2, notes = null)).toInt()
        transactionDao.addTagsToTransaction(listOf(TransactionTagCrossRef(tx1Id, tag1.id))) // Tag "Work"
        transactionDao.addTagsToTransaction(listOf(TransactionTagCrossRef(tx2Id, tag2.id))) // Tag "Vacation"

        // Act & Assert
        transactionDao.searchTransactions(keyword = "", null, null, null, startDate = yesterday - 1000, endDate = yesterday + 1000, tagId = tag2.id).test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("Vacation Flight", results.first().transaction.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchTransactions by keyword, accountId, and tagId`() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        // Target transaction
        val tx1Id = transactionDao.insert(Transaction(id = 1, description = "Work Project Dinner", amount = 1200.0, date = now, accountId = 1, categoryId = 1, notes = "Client meeting")).toInt()
        transactionDao.addTagsToTransaction(listOf(TransactionTagCrossRef(tx1Id, tag1.id))) // Tag "Work"
        // Decoy transactions
        transactionDao.insert(Transaction(id = 2, description = "Work Lunch", amount = 300.0, date = now, accountId = 2, categoryId = 1, notes = null)) // Wrong account
        transactionDao.insert(Transaction(id = 3, description = "Project Snacks", amount = 150.0, date = now, accountId = 1, categoryId = 1, notes = null)) // Wrong keyword/tag

        // Act & Assert
        transactionDao.searchTransactions(keyword = "Project", accountId = 1, null, null, null, null, tagId = tag1.id).test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("Work Project Dinner", results.first().transaction.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchTransactions by keyword matches notes`() = runTest {
        // Arrange
        val transaction = Transaction(id = 1, description = "Restaurant Bill", amount = 500.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = "Important business lunch")
        transactionDao.insert(transaction)

        // Act & Assert
        transactionDao.searchTransactions(keyword = "business", null, null, null, null, null, null).test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("Important business lunch", results.first().transaction.notes)
            cancelAndIgnoreRemainingEvents()
        }
    }
    // --- End of new search tests ---

    @Test
    fun `searchTransactions by accountId filters correctly`() = runTest {
        // Arrange
        transactionDao.insert(Transaction(id = 1, description = "tx1", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(id = 2, description = "tx2", amount = 200.0, date = System.currentTimeMillis(), accountId = 2, categoryId = 1, notes = null))

        // Act & Assert
        transactionDao.searchTransactions(keyword = "", accountId = 2, null, null, null, null, null).test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("tx2", results.first().transaction.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchTransactions by categoryId filters correctly`() = runTest {
        // Arrange
        transactionDao.insert(Transaction(id = 1, description = "tx1", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(id = 2, description = "tx2", amount = 200.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 2, notes = null))

        // Act & Assert
        transactionDao.searchTransactions(keyword = "", null, categoryId = 2, null, null, null, null).test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("tx2", results.first().transaction.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchTransactions by dateRange filters correctly`() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val yesterday = now - 86400000 // 24 hours in millis
        val twoDaysAgo = now - (2 * 86400000)

        transactionDao.insert(Transaction(id = 1, description = "today", amount = 100.0, date = now, accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(id = 2, description = "yesterday", amount = 200.0, date = yesterday, accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(id = 3, description = "two days ago", amount = 300.0, date = twoDaysAgo, accountId = 1, categoryId = 1, notes = null))

        // Act & Assert
        transactionDao.searchTransactions(keyword = "", null, null, null, startDate = yesterday - 1000, endDate = now, null).test {
            val results = awaitItem()
            assertEquals(2, results.size)
            assertTrue(results.any { it.transaction.description == "today" })
            assertTrue(results.any { it.transaction.description == "yesterday" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchTransactions by tagId filters correctly`() = runTest {
        // Arrange
        transactionDao.insert(Transaction(id = 1, description = "tx1", amount = 100.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(id = 2, description = "tx2", amount = 200.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 2, notes = null))
        transactionDao.addTagsToTransaction(listOf(TransactionTagCrossRef(transactionId = 1, tagId = 1)))
        transactionDao.addTagsToTransaction(listOf(TransactionTagCrossRef(transactionId = 2, tagId = 2)))

        // Act & Assert
        transactionDao.searchTransactions(keyword = "", null, null, null, null, null, tagId = 2).test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("tx2", results.first().transaction.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getFinancialSummaryForRangeFlow calculates income and expenses correctly`() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val startDate = now - TimeUnit.DAYS.toMillis(5)

        transactionDao.insert(Transaction(description = "Salary", amount = 50000.0, date = now - TimeUnit.DAYS.toMillis(1), transactionType = "income", accountId = 1, categoryId = 3, notes = null))
        transactionDao.insert(Transaction(description = "Rent", amount = 15000.0, date = now - TimeUnit.DAYS.toMillis(2), transactionType = "expense", accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(description = "Groceries", amount = 3500.0, date = now - TimeUnit.DAYS.toMillis(3), transactionType = "expense", accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(description = "Excluded Expense", amount = 1000.0, date = now - TimeUnit.DAYS.toMillis(4), transactionType = "expense", accountId = 1, categoryId = 1, notes = null, isExcluded = true))

        // Act & Assert
        transactionDao.getFinancialSummaryForRangeFlow(startDate, now).test {
            val summary = awaitItem()
            assertNotNull(summary)
            assertEquals(50000.0, summary!!.totalIncome, 0.01)
            assertEquals(18500.0, summary.totalExpenses, 0.01) // 15000 + 3500
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getSpendingByCategoryForMonth aggregates and orders correctly`() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val startDate = now - TimeUnit.DAYS.toMillis(10)
        transactionDao.insert(Transaction(description = "Flight", amount = 8000.0, date = now, transactionType = "expense", accountId = 1, categoryId = 2, notes = null))
        transactionDao.insert(Transaction(description = "Lunch", amount = 500.0, date = now, transactionType = "expense", accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(description = "Hotel", amount = 4000.0, date = now, transactionType = "expense", accountId = 1, categoryId = 2, notes = null))
        transactionDao.insert(Transaction(description = "Salary", amount = 50000.0, date = now, transactionType = "income", accountId = 1, categoryId = 3, notes = null)) // Should be ignored

        // Act & Assert
        transactionDao.getSpendingByCategoryForMonth(startDate, now, null, null, null).test {
            val spendingList = awaitItem()
            assertEquals(2, spendingList.size)

            // First item should be Travel (8000 + 4000)
            assertEquals("Travel", spendingList[0].categoryName)
            assertEquals(12000.0, spendingList[0].totalAmount, 0.01)

            // Second item should be Food
            assertEquals("Food", spendingList[1].categoryName)
            assertEquals(500.0, spendingList[1].totalAmount, 0.01)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAverageDailySpendingForRange calculates average correctly`() = runTest {
        // Arrange
        val threeDaysInMillis = TimeUnit.DAYS.toMillis(3)
        val endDate = System.currentTimeMillis()
        val startDate = endDate - threeDaysInMillis

        transactionDao.insert(Transaction(description = "Day 1 Spend", amount = 100.0, date = startDate + 1000, transactionType = "expense", accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(description = "Day 2 Spend", amount = 150.0, date = startDate + TimeUnit.DAYS.toMillis(1), transactionType = "expense", accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(description = "Day 3 Spend", amount = 50.0, date = endDate - 1000, transactionType = "expense", accountId = 1, categoryId = 1, notes = null))

        // Total spend = 300.0 over 3 days. Average should be 100.0
        val expectedAverage = 100.0

        // Act
        val average = transactionDao.getAverageDailySpendingForRange(startDate, endDate)

        // Assert
        assertNotNull(average)
        assertEquals(expectedAverage, average!!, 0.01)
    }

    @Test
    fun `getSpendingAnalysisByMerchant groups case-insensitive merchants correctly`() = runTest {
        // Arrange: Insert transactions for the same merchant with different casing.
        val transactionTime = System.currentTimeMillis()
        val startTime = transactionTime - TimeUnit.HOURS.toMillis(1)
        val endTime = transactionTime + TimeUnit.HOURS.toMillis(1)

        transactionDao.insert(Transaction(description = "Amazon", amount = 100.0, date = transactionTime, accountId = 1, categoryId = 1, transactionType = "expense", notes = null))
        transactionDao.insert(Transaction(description = "amazon", amount = 50.0, date = transactionTime, accountId = 1, categoryId = 1, transactionType = "expense", notes = null))
        transactionDao.insert(Transaction(description = "AMAZON", amount = 25.0, date = transactionTime, accountId = 1, categoryId = 1, transactionType = "expense", notes = null))
        // Add another distinct merchant for control
        transactionDao.insert(Transaction(description = "Flipkart", amount = 200.0, date = transactionTime, accountId = 1, categoryId = 1, transactionType = "expense", notes = null))

        // Act & Assert
        // --- FIX: Add the missing includeExcluded parameter ---
        transactionDao.getSpendingAnalysisByMerchant(startTime, endTime, null, null, null, null, false).test {
            val results = awaitItem()

            // Assert: There should be only two groups: one for all "Amazon" variations and one for "Flipkart".
            assertEquals(2, results.size)

            // Assert: Find the aggregated "Amazon" item. Its dimensionId MUST be lowercase.
            val amazonItem = results.find { it.dimensionId == "amazon" }
            assertNotNull("Aggregated item for 'amazon' should exist", amazonItem)

            // Assert: The total amount should be the sum of all three "Amazon" transactions.
            assertEquals(175.0, amazonItem!!.totalAmount, 0.01) // 100 + 50 + 25

            // Assert: The transaction count should be 3.
            assertEquals(3, amazonItem.transactionCount)

            // Assert: The dimensionName should be one of the original casings (MIN picks 'AMAZON').
            assertEquals("AMAZON", amazonItem.dimensionName)

            // Assert: The Flipkart item is also present and correct.
            val flipkartItem = results.find { it.dimensionId == "flipkart" }
            assertNotNull("Aggregated item for 'flipkart' should exist", flipkartItem)
            assertEquals(200.0, flipkartItem!!.totalAmount, 0.01)
            assertEquals(1, flipkartItem.transactionCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Test for includeExcluded = true on getSpendingAnalysisByMerchant ---
    @Test
    fun `getSpendingAnalysisByMerchant_respects_includeExcluded_flag`() = runTest {
        // Arrange
        val transactionTime = System.currentTimeMillis()
        val startTime = transactionTime - TimeUnit.HOURS.toMillis(1)
        val endTime = transactionTime + TimeUnit.HOURS.toMillis(1)

        // Insert one regular and one excluded transaction for the same merchant
        transactionDao.insert(Transaction(description = "Amazon", amount = 100.0, date = transactionTime, accountId = 1, categoryId = 1, transactionType = "expense", notes = null, isExcluded = false))
        transactionDao.insert(Transaction(description = "Amazon", amount = 50.0, date = transactionTime, accountId = 1, categoryId = 1, transactionType = "expense", notes = null, isExcluded = true))

        // Act 1: Call with includeExcluded = false (default behavior)
        transactionDao.getSpendingAnalysisByMerchant(startTime, endTime, null, null, null, null, false).test {
            val results = awaitItem()
            val amazonItem = results.find { it.dimensionId == "amazon" }
            assertNotNull(amazonItem)
            assertEquals(1, amazonItem!!.transactionCount)
            assertEquals(100.0, amazonItem.totalAmount, 0.01)
            cancelAndIgnoreRemainingEvents()
        }

        // Act 2: Call with includeExcluded = true (new behavior)
        transactionDao.getSpendingAnalysisByMerchant(startTime, endTime, null, null, null, null, true).test {
            val results = awaitItem()
            val amazonItem = results.find { it.dimensionId == "amazon" }
            assertNotNull(amazonItem)
            assertEquals(2, amazonItem!!.transactionCount)
            assertEquals(150.0, amazonItem.totalAmount, 0.01) // 100.0 + 50.0
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Test for includeExcluded = true on getSpendingAnalysisByCategory ---
    @Test
    fun `getSpendingAnalysisByCategory_respects_includeExcluded_flag`() = runTest {
        // Arrange
        val transactionTime = System.currentTimeMillis()
        val startTime = transactionTime - TimeUnit.HOURS.toMillis(1)
        val endTime = transactionTime + TimeUnit.HOURS.toMillis(1)

        // Insert one regular (Cat 1) and one excluded (Cat 1)
        transactionDao.insert(Transaction(description = "Lunch", amount = 100.0, date = transactionTime, accountId = 1, categoryId = 1, transactionType = "expense", notes = null, isExcluded = false))
        transactionDao.insert(Transaction(description = "Excluded Lunch", amount = 50.0, date = transactionTime, accountId = 1, categoryId = 1, transactionType = "expense", notes = null, isExcluded = true))

        // Act 1: Call with includeExcluded = false
        transactionDao.getSpendingAnalysisByCategory(startTime, endTime, null, null, null, null, false).test {
            val results = awaitItem()
            val foodItem = results.find { it.dimensionId == category1.id.toString() }
            assertNotNull(foodItem)
            assertEquals(1, foodItem!!.transactionCount)
            assertEquals(100.0, foodItem.totalAmount, 0.01)
            cancelAndIgnoreRemainingEvents()
        }

        // Act 2: Call with includeExcluded = true
        transactionDao.getSpendingAnalysisByCategory(startTime, endTime, null, null, null, null, true).test {
            val results = awaitItem()
            val foodItem = results.find { it.dimensionId == category1.id.toString() }
            assertNotNull(foodItem)
            assertEquals(2, foodItem!!.transactionCount)
            assertEquals(150.0, foodItem.totalAmount, 0.01) // 100.0 + 50.0
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- NEW: Test for includeExcluded = true on getSpendingAnalysisByTag ---
    @Test
    fun `getSpendingAnalysisByTag_respects_includeExcluded_flag`() = runTest {
        // Arrange
        val transactionTime = System.currentTimeMillis()
        val startTime = transactionTime - TimeUnit.HOURS.toMillis(1)
        val endTime = transactionTime + TimeUnit.HOURS.toMillis(1)

        // Insert one regular and one excluded, both with tag 1 ("Work")
        val tx1Id = transactionDao.insert(Transaction(description = "Work Lunch", amount = 100.0, date = transactionTime, accountId = 1, categoryId = 1, transactionType = "expense", notes = null, isExcluded = false)).toInt()
        val tx2Id = transactionDao.insert(Transaction(description = "Excluded Work Lunch", amount = 50.0, date = transactionTime, accountId = 1, categoryId = 1, transactionType = "expense", notes = null, isExcluded = true)).toInt()
        transactionDao.addTagsToTransaction(listOf(
            TransactionTagCrossRef(tx1Id, tag1.id),
            TransactionTagCrossRef(tx2Id, tag1.id)
        ))

        // Act 1: Call with includeExcluded = false
        transactionDao.getSpendingAnalysisByTag(startTime, endTime, null, null, null, null, false).test {
            val results = awaitItem()
            val workTagItem = results.find { it.dimensionId == tag1.id.toString() }
            assertNotNull(workTagItem)
            assertEquals(1, workTagItem!!.transactionCount)
            assertEquals(100.0, workTagItem.totalAmount, 0.01)
            cancelAndIgnoreRemainingEvents()
        }

        // Act 2: Call with includeExcluded = true
        transactionDao.getSpendingAnalysisByTag(startTime, endTime, null, null, null, null, true).test {
            val results = awaitItem()
            val workTagItem = results.find { it.dimensionId == tag1.id.toString() }
            assertNotNull(workTagItem)
            assertEquals(2, workTagItem!!.transactionCount)
            assertEquals(150.0, workTagItem.totalAmount, 0.01) // 100.0 + 50.0
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `updateTransactionType_modifiesTypeInDatabase`() = runTest {
        // Arrange
        val transaction = Transaction(id = 1, description = "Test", amount = 100.0, date = 1L, accountId = 1, categoryId = 1, notes = null, transactionType = "expense")
        transactionDao.insert(transaction)
        val newType = "income"

        // Act
        transactionDao.updateTransactionType(1, newType)

        // Assert
        transactionDao.getTransactionById(1).test {
            val updatedTx = awaitItem()
            assertNotNull(updatedTx)
            assertEquals("Transaction type should be updated", newType, updatedTx?.transactionType)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
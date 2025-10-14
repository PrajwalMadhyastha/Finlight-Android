package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

        // Pre-populate database with necessary entities
        accountDao.insertAll(listOf(account1, account2))
        categoryDao.insertAll(listOf(category1, category2, category3))
        tagDao.insertAll(listOf(tag1, tag2))
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

    @Test
    fun `searchTransactions by keyword matches description`() = runTest {
        // Arrange
        val transaction = Transaction(id = 1, description = "Lunch at Cafe", amount = 500.0, date = System.currentTimeMillis(), accountId = 1, categoryId = 1, notes = "Team lunch")
        transactionDao.insert(transaction)

        // Act & Assert
        transactionDao.searchTransactions(keyword = "Cafe", null, null, null, null, null, null).test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("Lunch at Cafe", results.first().transaction.description)
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
}
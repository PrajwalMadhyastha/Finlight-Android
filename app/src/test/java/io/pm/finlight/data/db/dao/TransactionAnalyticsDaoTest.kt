package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TransactionAnalyticsDaoTest {
    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var analyticsDao: TransactionAnalyticsDao
    private lateinit var writeDao: TransactionWriteDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var tagDao: TagDao

    private val account1 = Account(id = 1, name = "Savings", type = "Bank")
    private val category1 = Category(id = 1, name = "Food & Dining", iconKey = "food", colorKey = "red")
    private val category2 = Category(id = 2, name = "Salary", iconKey = "work", colorKey = "green")
    private val tag1 = Tag(id = 1, name = "Vacation")

    @Before
    fun setup() =
        runTest {
            analyticsDao = dbRule.db.transactionAnalyticsDao()
            writeDao = dbRule.db.transactionWriteDao()
            accountDao = dbRule.db.accountDao()
            categoryDao = dbRule.db.categoryDao()
            tagDao = dbRule.db.tagDao()

            accountDao.insertAll(listOf(account1))
            categoryDao.insertAll(listOf(category1, category2))
            tagDao.insertAll(listOf(tag1))
        }

    @Test
    fun testSpendingAnalysisByCategory() =
        runTest {
            val t1 = Transaction(description = "Burger", amount = 100.0, date = 5000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            val t2 = Transaction(description = "Pizza", amount = 200.0, date = 6000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            writeDao.insert(t1)
            writeDao.insert(t2)

            val analysis =
                analyticsDao.getSpendingAnalysisByCategory(
                    startDate = 1000L,
                    endDate = 10000L,
                    filterTagId = null,
                    filterMerchantName = null,
                    filterCategoryId = null,
                    searchQuery = null,
                    includeExcluded = false,
                    transactionType = TransactionType.EXPENSE
                ).first()

            assertEquals(1, analysis.size)
            assertEquals("Food & Dining", analysis.first().dimensionName)
            assertEquals(300.0, analysis.first().totalAmount)
            assertEquals(2, analysis.first().transactionCount)
        }

    @Test
    fun testFinancialSummaryForRange() =
        runTest {
            val expense = Transaction(description = "Coffee", amount = 50.0, date = 5000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            val income = Transaction(description = "Paycheck", amount = 1000.0, date = 6000L, transactionType = TransactionType.INCOME, accountId = 1, categoryId = 2, notes = null, source = "Manual")
            writeDao.insert(expense)
            writeDao.insert(income)

            val summary = analyticsDao.getFinancialSummaryForRange(1000L, 10000L)
            assertNotNull(summary)
            assertEquals(50.0, summary.totalExpenses)
            assertEquals(1000.0, summary.totalIncome)

            val flowSummary = analyticsDao.getFinancialSummaryForRangeFlow(1000L, 10000L).first()
            assertNotNull(flowSummary)
            assertEquals(50.0, flowSummary.totalExpenses)
            assertEquals(1000.0, flowSummary.totalIncome)
        }

    @Test
    fun testTopSpendingCategoriesAndAverage() =
        runTest {
            val t1 = Transaction(description = "Lunch", amount = 150.0, date = 5000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            writeDao.insert(t1)

            val top = analyticsDao.getTopSpendingCategoriesForRange(1000L, 10000L)
            assertEquals(1, top.size)
            assertEquals("Food & Dining", top.first().categoryName)
            assertEquals(150.0, top.first().totalAmount)

            val avg = analyticsDao.getAverageDailySpendingForRange(1000L, 10000L)
            assertNotNull(avg)
            assertTrue(avg > 0.0)
        }

    @Test
    fun testTransactionsForCategoryInRange() =
        runTest {
            val t1 = Transaction(description = "Snack", amount = 25.0, date = 5000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            writeDao.insert(t1)

            val details = analyticsDao.getTransactionsForCategoryInRange(1, 1000L, 10000L).first()
            assertEquals(1, details.size)
            assertEquals("Snack", details.first().transaction.description)
        }

    @Test
    fun testSpendingAnalysisEmptyRange() =
        runTest {
            val analysis =
                analyticsDao.getSpendingAnalysisByCategory(
                    startDate = 50000L,
                    endDate = 60000L,
                    filterTagId = null,
                    filterMerchantName = null,
                    filterCategoryId = null,
                    searchQuery = null,
                    includeExcluded = false,
                    transactionType = TransactionType.EXPENSE,
                ).first()

            assertTrue(analysis.isEmpty())
        }

    @Test
    fun testSpendingAnalysisByTagAndMerchant() =
        runTest {
            val t1 = Transaction(description = "Resort", amount = 500.0, date = 5000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            val id = writeDao.insert(t1).toInt()
            writeDao.addTagsToTransaction(listOf(TransactionTagCrossRef(id, 1)))

            val byTag =
                analyticsDao.getSpendingAnalysisByTag(
                    startDate = 1000L,
                    endDate = 10000L,
                    filterCategoryId = null,
                    filterMerchantName = null,
                    filterTagId = null,
                    searchQuery = null,
                    includeExcluded = false,
                    transactionType = TransactionType.EXPENSE,
                ).first()
            assertEquals(1, byTag.size)
            assertEquals("Vacation", byTag.first().dimensionName)
            assertEquals(500.0, byTag.first().totalAmount)

            val byMerchant =
                analyticsDao.getSpendingAnalysisByMerchant(
                    startDate = 1000L,
                    endDate = 10000L,
                    filterCategoryId = null,
                    filterMerchantName = null,
                    filterTagId = null,
                    searchQuery = null,
                    includeExcluded = false,
                    transactionType = TransactionType.EXPENSE,
                ).first()
            assertEquals(1, byMerchant.size)
            assertEquals("Resort", byMerchant.first().dimensionName)
            assertEquals(500.0, byMerchant.first().totalAmount)
        }

    @Test
    fun testTrendsForRange() =
        runTest {
            val t1 = Transaction(description = "Groceries", amount = 100.0, date = 5000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            val t2 = Transaction(description = "Salary", amount = 2000.0, date = 5000L, transactionType = TransactionType.INCOME, accountId = 1, categoryId = 2, notes = null, source = "Manual")
            writeDao.insert(t1)
            writeDao.insert(t2)

            val dailyTrends = analyticsDao.getDailyTrends(1000L, 10000L).first()
            assertEquals(1, dailyTrends.size)
            assertEquals(100.0, dailyTrends.first().totalExpenses)
            assertEquals(2000.0, dailyTrends.first().totalIncome)

            val weeklyTrends = analyticsDao.getWeeklyTrends(1000L, 10000L).first()
            assertEquals(1, weeklyTrends.size)

            val monthlyTrends = analyticsDao.getMonthlyTrends(1000L).first()
            assertEquals(1, monthlyTrends.size)
        }

    @Test
    fun testTotalExpensesSinceAndExpenseMerchants() =
        runTest {
            val t1 = Transaction(description = "Uber", amount = 45.0, date = 5000L, transactionType = TransactionType.EXPENSE, accountId = 1, categoryId = 1, notes = null, source = "Manual")
            writeDao.insert(t1)

            val total = analyticsDao.getTotalExpensesSince(1000L)
            assertEquals(45.0, total)

            val merchants = analyticsDao.getAllExpenseMerchants().first()
            assertEquals(listOf("Uber"), merchants)
        }
}

package io.pm.finlight.data.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.CategoryDao
import io.pm.finlight.CategorySpending
import io.pm.finlight.FinancialSummary
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest : BaseDaoTest() {

    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setup() {
        runBlocking {
            transactionDao = db.transactionDao()
            accountDao = db.accountDao()
            categoryDao = db.categoryDao()

            accountDao.insert(Account(id = 1, name = "Wallet", type = "Cash"))
            categoryDao.insert(Category(id = 1, name = "Food", iconKey = "icon", colorKey = "red"))
            categoryDao.insert(Category(id = 2, name = "Transport", iconKey = "icon", colorKey = "blue"))
        }
    }

    @Test
    fun getFinancialSummaryForRange_aggregatesCorrectly() {
        runBlocking {
            val today = System.currentTimeMillis()

            // Expense 1: -100
            transactionDao.insert(Transaction(
                description = "Lunch", amount = 100.0, transactionType = "expense",
                date = today, accountId = 1, categoryId = 1, notes = null
            ))

            // Expense 2: -50
            transactionDao.insert(Transaction(
                description = "Bus", amount = 50.0, transactionType = "expense",
                date = today, accountId = 1, categoryId = 2, notes = null
            ))

            // Income: +500
            transactionDao.insert(Transaction(
                description = "Refund", amount = 500.0, transactionType = "income",
                date = today, accountId = 1, categoryId = null, notes = null
            ))

            // Expense 3 (Outside range): -1000
            val lastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis
            transactionDao.insert(Transaction(
                description = "Old", amount = 1000.0, transactionType = "expense",
                date = lastMonth, accountId = 1, categoryId = 1, notes = null
            ))

            // Act: Query for today
            val start = today - 1000
            val end = today + 1000
            val summary = transactionDao.getFinancialSummaryForRange(start, end)

            // Assert
            assertNotNull(summary)
            assertEquals("Total Income", 500.0, summary!!.totalIncome, 0.01)
            assertEquals("Total Expenses (100+50)", 150.0, summary.totalExpenses, 0.01)
        }
    }

    @Test
    fun getSpendingByCategoryForMonth_groupsCorrectly() {
        runBlocking {
            val today = System.currentTimeMillis()

            // 2 Food transactions, 1 Transport
            transactionDao.insert(Transaction(description = "Food 1", amount = 10.0, transactionType = "expense", date = today, accountId = 1, categoryId = 1, notes = null))
            transactionDao.insert(Transaction(description = "Food 2", amount = 20.0, transactionType = "expense", date = today, accountId = 1, categoryId = 1, notes = null))
            transactionDao.insert(Transaction(description = "Taxi", amount = 50.0, transactionType = "expense", date = today, accountId = 1, categoryId = 2, notes = null))

            // Act
            val start = today - 1000
            val end = today + 1000

            // collect the flow
            val spendingListFlow = transactionDao.getSpendingByCategoryForMonth(start, end, null, null, null)
            val spendingList = spendingListFlow.first()

            // Assert
            assertEquals(2, spendingList.size)

            val foodStats = spendingList.find { it.categoryName == "Food" }
            assertNotNull(foodStats)
            assertEquals(30.0, foodStats!!.totalAmount, 0.01)

            val transportStats = spendingList.find { it.categoryName == "Transport" }
            assertNotNull(transportStats)
            assertEquals(50.0, transportStats!!.totalAmount, 0.01)
        }
    }
}
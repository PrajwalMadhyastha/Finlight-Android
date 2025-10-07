package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BudgetDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var budgetDao: BudgetDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var accountDao: AccountDao

    private val foodCategory = Category(id = 1, name = "Food", iconKey = "restaurant", colorKey = "red")
    private val travelCategory = Category(id = 2, name = "Travel", iconKey = "travel", colorKey = "blue")
    private val account = Account(id = 1, name = "Savings", type = "Bank")

    @Before
    fun setup() = runTest {
        budgetDao = dbRule.db.budgetDao()
        transactionDao = dbRule.db.transactionDao()
        categoryDao = dbRule.db.categoryDao()
        accountDao = dbRule.db.accountDao()

        categoryDao.insertAll(listOf(foodCategory, travelCategory))
        accountDao.insert(account)
    }

    @Test
    fun `getBudgetsWithSpendingForMonth calculates spending correctly`() = runTest {
        // Arrange
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val yearMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)

        budgetDao.insert(Budget(categoryName = "Food", amount = 5000.0, month = month, year = year))
        transactionDao.insert(Transaction(description = "Lunch", amount = 300.0, date = cal.timeInMillis, transactionType = "expense", accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(description = "Dinner", amount = 700.0, date = cal.timeInMillis, transactionType = "expense", accountId = 1, categoryId = 1, notes = null))

        // Act & Assert
        budgetDao.getBudgetsWithSpendingForMonth(yearMonth, month, year).test {
            val budgets = awaitItem()
            assertEquals(1, budgets.size)
            val foodBudget = budgets.first()
            assertEquals("Food", foodBudget.budget.categoryName)
            assertEquals(5000.0, foodBudget.budget.amount, 0.01)
            assertEquals(1000.0, foodBudget.spent, 0.01)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getBudgetsWithSpendingForMonth carries over budget from previous month`() = runTest {
        // Arrange: Budget is for last month, transactions are for this month
        val lastMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val lastMonthYear = lastMonthCal.get(Calendar.YEAR)
        val lastMonth = lastMonthCal.get(Calendar.MONTH) + 1

        val currentCal = Calendar.getInstance()
        val currentYear = currentCal.get(Calendar.YEAR)
        val currentMonth = currentCal.get(Calendar.MONTH) + 1
        val currentYearMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentCal.time)

        // Budget for "Food" was set last month
        budgetDao.insert(Budget(categoryName = "Food", amount = 8000.0, month = lastMonth, year = lastMonthYear))
        // Spending happens this month
        transactionDao.insert(Transaction(description = "Groceries", amount = 2500.0, date = currentCal.timeInMillis, transactionType = "expense", accountId = 1, categoryId = 1, notes = null))

        // Act & Assert
        budgetDao.getBudgetsWithSpendingForMonth(currentYearMonth, currentMonth, currentYear).test {
            val budgets = awaitItem()
            assertEquals(1, budgets.size)
            val foodBudget = budgets.first()
            assertEquals("Food", foodBudget.budget.categoryName)
            // It should use last month's budget amount
            assertEquals(8000.0, foodBudget.budget.amount, 0.01)
            // It should calculate this month's spending
            assertEquals(2500.0, foodBudget.spent, 0.01)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getBudgetsWithSpendingForMonth prefers current month budget over carry-over`() = runTest {
        // Arrange
        val lastMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val currentCal = Calendar.getInstance()
        val currentYear = currentCal.get(Calendar.YEAR)
        val currentMonth = currentCal.get(Calendar.MONTH) + 1
        val currentYearMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentCal.time)

        // Set budgets for both last month and this month for the same category
        budgetDao.insert(Budget(categoryName = "Travel", amount = 20000.0, month = lastMonthCal.get(Calendar.MONTH) + 1, year = lastMonthCal.get(Calendar.YEAR)))
        budgetDao.insert(Budget(categoryName = "Travel", amount = 15000.0, month = currentMonth, year = currentYear))

        // Act & Assert
        budgetDao.getBudgetsWithSpendingForMonth(currentYearMonth, currentMonth, currentYear).test {
            val budgets = awaitItem()
            val travelBudget = budgets.find { it.budget.categoryName == "Travel" }
            assertNotNull(travelBudget)
            // It should use this month's budget amount
            assertEquals(15000.0, travelBudget!!.budget.amount, 0.01)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getBudgetsForMonth returns budgets for specific month only`() = runTest {
        // Arrange
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val lastMonth = cal.get(Calendar.MONTH)

        budgetDao.insert(Budget(categoryName = "Food", amount = 5000.0, month = month, year = year))
        budgetDao.insert(Budget(categoryName = "Travel", amount = 10000.0, month = lastMonth, year = year))

        // Act & Assert
        budgetDao.getBudgetsForMonth(month, year).test {
            val budgets = awaitItem()
            assertEquals(1, budgets.size)
            assertEquals("Food", budgets.first().categoryName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getActualSpendingForCategory calculates correctly`() = runTest {
        // Arrange
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        transactionDao.insert(Transaction(description = "Lunch", amount = 300.0, date = cal.timeInMillis, transactionType = "expense", accountId = 1, categoryId = 1, notes = null))
        transactionDao.insert(Transaction(description = "Dinner", amount = 700.0, date = cal.timeInMillis, transactionType = "expense", accountId = 1, categoryId = 1, notes = null))

        // Act & Assert
        budgetDao.getActualSpendingForCategory("Food", month, year).test {
            val spending = awaitItem()
            assertEquals(1000.0, spending ?: 0.0, 0.01)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllBudgets returns all budgets from all months`() = runTest {
        // Arrange
        budgetDao.insert(Budget(categoryName = "Food", amount = 1.0, month = 1, year = 2025))
        budgetDao.insert(Budget(categoryName = "Travel", amount = 2.0, month = 2, year = 2025))

        // Act & Assert
        budgetDao.getAllBudgets().test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getById returns correct budget`() = runTest {
        // Arrange
        budgetDao.insert(Budget(categoryName = "Food", amount = 1.0, month = 1, year = 2025))
        val id = budgetDao.getAllBudgets().first().first().id

        // Act & Assert
        budgetDao.getById(id).test {
            val budget = awaitItem()
            assertNotNull(budget)
            assertEquals("Food", budget?.categoryName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update modifies budget correctly`() = runTest {
        // Arrange
        budgetDao.insert(Budget(categoryName = "Food", amount = 100.0, month = 1, year = 2025))
        val id = budgetDao.getAllBudgets().first().first().id
        val updatedBudget = Budget(id = id, categoryName = "Food", amount = 200.0, month = 1, year = 2025)

        // Act
        budgetDao.update(updatedBudget)

        // Assert
        budgetDao.getById(id).test {
            assertEquals(200.0, awaitItem()?.amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete removes budget`() = runTest {
        // Arrange
        val budget = Budget(categoryName = "Food", amount = 100.0, month = 1, year = 2025)
        budgetDao.insert(budget)
        val id = budgetDao.getAllBudgets().first().first().id

        // Act
        budgetDao.delete(budget.copy(id=id))

        // Assert
        budgetDao.getById(id).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll removes all budgets`() = runTest {
        // Arrange
        budgetDao.insertAll(listOf(
            Budget(categoryName = "Food", amount = 1.0, month = 1, year = 2025),
            Budget(categoryName = "Travel", amount = 2.0, month = 2, year = 2025)
        ))

        // Act
        budgetDao.deleteAll()

        // Assert
        budgetDao.getAllBudgets().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
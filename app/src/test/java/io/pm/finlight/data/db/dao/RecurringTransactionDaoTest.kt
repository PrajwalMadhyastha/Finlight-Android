package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.Account
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.RecurringTransactionDao
import io.pm.finlight.TestApplication
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class RecurringTransactionDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var recurringTransactionDao: RecurringTransactionDao
    private lateinit var accountDao: AccountDao

    @Before
    fun setup() = runTest {
        recurringTransactionDao = dbRule.db.recurringTransactionDao()
        accountDao = dbRule.db.accountDao()
        accountDao.insert(Account(id = 1, name = "Savings", type = "Bank"))
    }

    @Test
    fun `insert returns new row id`() = runTest {
        // Arrange
        val rule = RecurringTransaction(description = "Netflix", amount = 149.0, transactionType = "expense", recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = null)

        // Act
        val newId = recurringTransactionDao.insert(rule)

        // Assert
        assertEquals(1L, newId)
    }

    @Test
    fun `updateLastRunDate correctly updates the timestamp`() = runTest {
        // Arrange
        val ruleId = recurringTransactionDao.insert(
            RecurringTransaction(description = "Spotify", amount = 129.0, transactionType = "expense", recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = null)
        ).toInt()

        val newTimestamp = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis

        // Act
        recurringTransactionDao.updateLastRunDate(ruleId, newTimestamp)

        // Assert
        recurringTransactionDao.getById(ruleId).test {
            val updatedRule = awaitItem()
            assertNotNull(updatedRule)
            assertEquals(newTimestamp, updatedRule?.lastRunDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update modifies rule correctly`() = runTest {
        // Arrange
        val id = recurringTransactionDao.insert(RecurringTransaction(description = "Old", amount = 1.0, transactionType = "expense", recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = null)).toInt()
        val updatedRule = RecurringTransaction(id = id, description = "New", amount = 2.0, transactionType = "income", recurrenceInterval = "Weekly", startDate = 1L, accountId = 1, categoryId = null)

        // Act
        recurringTransactionDao.update(updatedRule)

        // Assert
        recurringTransactionDao.getById(id).test {
            val fromDb = awaitItem()
            assertEquals("New", fromDb?.description)
            assertEquals(2.0, fromDb?.amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete removes a rule`() = runTest {
        // Arrange
        val rule = RecurringTransaction(description = "Test", amount = 1.0, transactionType = "expense", recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = null)
        val id = recurringTransactionDao.insert(rule).toInt()

        // Act
        recurringTransactionDao.delete(rule.copy(id = id))

        // Assert
        recurringTransactionDao.getById(id).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllRulesFlow and getAllRulesList return all rules`() = runTest {
        // Arrange
        val rules = listOf(
            RecurringTransaction(description = "Rule1", amount = 1.0, transactionType = "expense", recurrenceInterval = "Monthly", startDate = 0L, accountId = 1, categoryId = null),
            RecurringTransaction(description = "Rule2", amount = 2.0, transactionType = "expense", recurrenceInterval = "Weekly", startDate = 0L, accountId = 1, categoryId = null)
        )
        rules.forEach { recurringTransactionDao.insert(it) }

        // Act & Assert for Flow
        recurringTransactionDao.getAllRulesFlow().test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }

        // Act & Assert for List
        val listResult = recurringTransactionDao.getAllRulesList()
        assertEquals(2, listResult.size)
    }
}
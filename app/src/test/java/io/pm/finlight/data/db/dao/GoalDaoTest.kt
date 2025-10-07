package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.Account
import io.pm.finlight.Goal
import io.pm.finlight.GoalDao
import io.pm.finlight.TestApplication
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class GoalDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var goalDao: GoalDao
    private lateinit var accountDao: AccountDao

    private var accountId: Int = 0

    @Before
    fun setup() = runTest {
        goalDao = dbRule.db.goalDao()
        accountDao = dbRule.db.accountDao()
        accountId = accountDao.insert(Account(name = "Savings", type = "Bank")).toInt()
    }

    @Test
    fun `getAllGoalsWithAccountName returns goal with correct account name`() = runTest {
        // Arrange
        val goal = Goal(name = "Trip to Japan", targetAmount = 200000.0, savedAmount = 25000.0, targetDate = null, accountId = accountId)
        goalDao.insert(goal)

        // Act & Assert
        goalDao.getAllGoalsWithAccountName().test {
            val goalsWithAccount = awaitItem()
            assertEquals(1, goalsWithAccount.size)
            val result = goalsWithAccount.first()

            assertEquals("Trip to Japan", result.name)
            assertEquals("Savings", result.accountName)
            assertEquals(accountId, result.accountId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reassignGoals updates accountId for specified goals`() = runTest {
        // Arrange
        val sourceAccountId = accountDao.insert(Account(name = "Source", type = "Bank")).toInt()
        val destAccountId = accountDao.insert(Account(name = "Destination", type = "Bank")).toInt()
        goalDao.insert(Goal(name = "Goal 1", targetAmount = 100.0, savedAmount = 0.0, targetDate = null, accountId = sourceAccountId))
        goalDao.insert(Goal(name = "Goal 2", targetAmount = 200.0, savedAmount = 0.0, targetDate = null, accountId = sourceAccountId))

        // Act
        goalDao.reassignGoals(listOf(sourceAccountId), destAccountId)

        // Assert
        goalDao.getAllGoalsWithAccountName().test {
            val updatedGoals = awaitItem()
            assertEquals(2, updatedGoals.size)
            assertTrue(updatedGoals.all { it.accountId == destAccountId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update modifies goal correctly`() = runTest {
        // Arrange
        val goal = Goal(name = "Old Name", targetAmount = 100.0, savedAmount = 0.0, targetDate = null, accountId = accountId)
        goalDao.insert(goal)
        val insertedGoal = goalDao.getAll().first()
        val updatedGoal = insertedGoal.copy(name = "New Name", savedAmount = 50.0)

        // Act
        goalDao.update(updatedGoal)

        // Assert
        goalDao.getGoalById(insertedGoal.id).test {
            val fromDb = awaitItem()
            assertEquals("New Name", fromDb?.name)
            assertEquals(50.0, fromDb?.savedAmount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete removes goal`() = runTest {
        // Arrange
        val goal = Goal(name = "To Delete", targetAmount = 100.0, savedAmount = 0.0, targetDate = null, accountId = accountId)
        goalDao.insert(goal)
        val insertedGoal = goalDao.getAll().first()

        // Act
        goalDao.delete(insertedGoal)

        // Assert
        goalDao.getGoalById(insertedGoal.id).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll removes all goals`() = runTest {
        // Arrange
        goalDao.insertAll(listOf(
            Goal(name = "Goal 1", targetAmount = 100.0, savedAmount = 0.0, targetDate = null, accountId = accountId),
            Goal(name = "Goal 2", targetAmount = 200.0, savedAmount = 0.0, targetDate = null, accountId = accountId)
        ))

        // Act
        goalDao.deleteAll()

        // Assert
        assertTrue(goalDao.getAll().isEmpty())
    }

    @Test
    fun `getGoalsForAccount returns only goals for that account`() = runTest {
        // Arrange
        val otherAccountId = accountDao.insert(Account(name = "Other", type = "Bank")).toInt()
        goalDao.insert(Goal(name = "Goal 1", targetAmount = 100.0, savedAmount = 0.0, targetDate = null, accountId = accountId))
        goalDao.insert(Goal(name = "Goal 2", targetAmount = 200.0, savedAmount = 0.0, targetDate = null, accountId = otherAccountId))

        // Act & Assert
        goalDao.getGoalsForAccount(accountId).test {
            val goals = awaitItem()
            assertEquals(1, goals.size)
            assertEquals("Goal 1", goals.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
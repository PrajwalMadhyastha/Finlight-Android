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
class GoalDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var goalDao: GoalDao
    private lateinit var accountDao: AccountDao

    @Before
    fun setup() = runTest {
        goalDao = dbRule.db.goalDao()
        accountDao = dbRule.db.accountDao()
    }

    @Test
    fun `getAllGoalsWithAccountName returns goal with correct account name`() = runTest {
        // Arrange
        val accountId = accountDao.insert(Account(name = "Vacation Fund", type = "Savings")).toInt()
        val goal = Goal(name = "Trip to Japan", targetAmount = 200000.0, savedAmount = 25000.0, targetDate = null, accountId = accountId)
        goalDao.insert(goal)

        // Act & Assert
        goalDao.getAllGoalsWithAccountName().test {
            val goalsWithAccount = awaitItem()
            assertEquals(1, goalsWithAccount.size)
            val result = goalsWithAccount.first()

            assertEquals("Trip to Japan", result.name)
            assertEquals("Vacation Fund", result.accountName)
            assertEquals(accountId, result.accountId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reassignGoals updates accountId for specified goals`() = runTest {
        // Arrange
        val sourceAccountId = accountDao.insert(Account(name = "Source", type = "Bank")).toInt()
        val destAccountId = accountDao.insert(Account(name = "Destination", type = "Bank")).toInt()
        // Insert goals without capturing their IDs, as the insert method returns Unit
        goalDao.insert(Goal(name = "Goal 1", targetAmount = 100.0, savedAmount = 0.0, targetDate = null, accountId = sourceAccountId))
        goalDao.insert(Goal(name = "Goal 2", targetAmount = 200.0, savedAmount = 0.0, targetDate = null, accountId = sourceAccountId))

        // Act
        goalDao.reassignGoals(listOf(sourceAccountId), destAccountId)

        // Assert
        // Fetch all goals and verify their accountId has been updated
        goalDao.getAllGoalsWithAccountName().test {
            val updatedGoals = awaitItem()
            assertEquals(2, updatedGoals.size)
            // Check that all goals now belong to the destination account
            assertTrue(updatedGoals.all { it.accountId == destAccountId })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
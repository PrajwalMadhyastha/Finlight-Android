package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.Goal
import io.pm.finlight.GoalDao
import io.pm.finlight.GoalRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionDao
import io.pm.finlight.data.db.dao.GoalTransactionLinkDao
import io.pm.finlight.data.db.dao.GoalContributionDao
import io.pm.finlight.data.db.entity.GoalContribution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.verify
import org.mockito.kotlin.argThat
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class GoalRepositoryTest : BaseViewModelTest() {
    @Mock
    private lateinit var goalDao: GoalDao

    @Mock
    private lateinit var linkDao: GoalTransactionLinkDao

    @Mock
    private lateinit var transactionDao: TransactionDao

    @Mock
    private lateinit var contributionDao: GoalContributionDao

    private lateinit var repository: GoalRepository

    @Before
    override fun setup() {
        super.setup()
        repository = GoalRepository(goalDao, linkDao, transactionDao, contributionDao)
    }

    @Test
    fun `getAllGoalsWithAccountName calls DAO`() {
        // Act
        repository.getAllGoalsWithAccountName()
        // Assert
        verify(goalDao).getAllGoalsWithAccountName()
    }

    @Test
    fun `getGoalById calls DAO`() {
        // Arrange
        val id = 1
        // Act
        repository.getGoalById(id)
        // Assert
        verify(goalDao).getGoalById(id)
    }

    @Test
    fun `insert calls DAO`() =
        runTest {
            // Arrange
            val goal = Goal(name = "Test", targetAmount = 100.0, targetDate = null, accountId = 1)
            // Act
            repository.insert(goal)
            // Assert
            verify(goalDao).insert(goal)
        }

    @Test
    fun `update calls DAO`() =
        runTest {
            // Arrange
            val goal = Goal(id = 1, name = "Test", targetAmount = 100.0, targetDate = null, accountId = 1)
            // Act
            repository.update(goal)
            // Assert
            verify(goalDao).update(goal)
        }

    @Test
    fun `delete calls DAO`() =
        runTest {
            // Arrange
            val goal = Goal(id = 1, name = "Test", targetAmount = 100.0, targetDate = null, accountId = 1)
            // Act
            repository.delete(goal)
            // Assert
            verify(goalDao).delete(goal)
        }

    @Test
    fun `getActiveGoals calls DAO`() {
        repository.getActiveGoals()
        verify(goalDao).getActiveGoals(anyLong())
    }

    @Test
    fun `getActiveGoalsSnapshot calls DAO`() =
        runTest {
            repository.getActiveGoalsSnapshot()
            verify(goalDao).getActiveGoalsSnapshot(anyLong())
        }

    @Test
    fun `getRecentTransactions calls TransactionDao`() {
        repository.getRecentTransactions(0L, 100L)
        verify(transactionDao).getAllTransactionsForRange(0L, 100L)
    }

    @Test
    fun `linkTransaction calls linkDao insertLink`() =
        runTest {
            repository.linkTransaction(1, 2)
            // Use argThat to avoid flakiness caused by GoalTransactionLink's
            // `linkedAt = System.currentTimeMillis()` default arg differing
            // between the actual call and the verify() call.
            verify(linkDao).insertLink(
                argThat { link -> link.goalId == 1 && link.transactionId == 2 },
            )
        }

    @Test
    fun `unlinkTransaction calls linkDao deleteLink`() =
        runTest {
            repository.unlinkTransaction(1, 2)
            verify(linkDao).deleteLink(1, 2)
        }

    @Test
    fun `getLinkedTransactions calls linkDao`() {
        repository.getLinkedTransactions(1)
        verify(linkDao).getLinkedTransactions(1)
    }

    @Test
    fun `getLinkedTotal calls linkDao`() {
        repository.getLinkedTotal(1)
        verify(linkDao).getLinkedTransactionsTotal(1)
    }

    @Test
    fun `getContributionsForGoal calls contributionDao`() {
        repository.getContributionsForGoal(1)
        verify(contributionDao).getContributionsForGoal(1)
    }

    @Test
    fun `getTotalContributionForGoal calls contributionDao`() {
        repository.getTotalContributionForGoal(1)
        verify(contributionDao).getTotalContributionForGoal(1)
    }

    @Test
    fun `insertContribution calls contributionDao`() =
        runTest {
            val contribution = GoalContribution(goalId = 1, amount = 100.0, date = 1000L, description = "Test")
            repository.insertContribution(contribution)
            verify(contributionDao).insertContribution(contribution)
        }

    @Test
    fun `updateContribution calls contributionDao`() =
        runTest {
            val contribution = GoalContribution(id = 1, goalId = 1, amount = 100.0, date = 1000L, description = "Test")
            repository.updateContribution(contribution)
            verify(contributionDao).updateContribution(contribution)
        }

    @Test
    fun `deleteContribution calls contributionDao`() =
        runTest {
            val contribution = GoalContribution(id = 1, goalId = 1, amount = 100.0, date = 1000L, description = "Test")
            repository.deleteContribution(contribution)
            verify(contributionDao).deleteContribution(contribution)
        }
}

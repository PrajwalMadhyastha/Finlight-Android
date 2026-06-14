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
import io.pm.finlight.data.db.entity.GoalTransactionLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
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

    private lateinit var repository: GoalRepository

    @Before
    override fun setup() {
        super.setup()
        repository = GoalRepository(goalDao, linkDao, transactionDao)
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
        verify(goalDao).getActiveGoals()
    }

    @Test
    fun `getActiveGoalsSnapshot calls DAO`() =
        runTest {
            repository.getActiveGoalsSnapshot()
            verify(goalDao).getActiveGoalsSnapshot()
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
            verify(linkDao).insertLink(GoalTransactionLink(goalId = 1, transactionId = 2))
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
}

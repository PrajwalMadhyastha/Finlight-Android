package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.Goal
import io.pm.finlight.GoalDao
import io.pm.finlight.GoalRepository
import io.pm.finlight.TestApplication
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

    private lateinit var repository: GoalRepository

    @Before
    override fun setup() {
        super.setup()
        repository = GoalRepository(goalDao)
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
    fun `insert calls DAO`() = runTest {
        // Arrange
        val goal = Goal(name = "Test", targetAmount = 100.0, savedAmount = 10.0, targetDate = null, accountId = 1)
        // Act
        repository.insert(goal)
        // Assert
        verify(goalDao).insert(goal)
    }

    @Test
    fun `update calls DAO`() = runTest {
        // Arrange
        val goal = Goal(id = 1, name = "Test", targetAmount = 100.0, savedAmount = 10.0, targetDate = null, accountId = 1)
        // Act
        repository.update(goal)
        // Assert
        verify(goalDao).update(goal)
    }

    @Test
    fun `delete calls DAO`() = runTest {
        // Arrange
        val goal = Goal(id = 1, name = "Test", targetAmount = 100.0, savedAmount = 10.0, targetDate = null, accountId = 1)
        // Act
        repository.delete(goal)
        // Assert
        verify(goalDao).delete(goal)
    }
}


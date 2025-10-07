package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.robolectric.annotation.Config
import java.lang.RuntimeException

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class GoalViewModelTest : BaseViewModelTest() {

    @Mock
    private lateinit var goalRepository: GoalRepository

    @Captor
    private lateinit var goalCaptor: ArgumentCaptor<Goal>

    private lateinit var viewModel: GoalViewModel

    @Before
    override fun setup() {
        super.setup()
        initializeViewModel()
    }

    private fun initializeViewModel(initialGoals: List<GoalWithAccountName> = emptyList()) {
        `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(initialGoals))
        viewModel = GoalViewModel(goalRepository)
    }

    @Test
    fun `allGoals flow emits goals from repository`() = runTest {
        // Arrange
        val goals = listOf(
            GoalWithAccountName(1, "Vacation", 50000.0, 10000.0, null, 1, "Savings")
        )
        initializeViewModel(goals)

        // Assert
        viewModel.allGoals.test {
            assertEquals(goals, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getGoalById calls repository getGoalById`() = runTest {
        // Arrange
        val goalId = 1
        val goal = Goal(goalId, "Vacation", 50000.0, 10000.0, null, 1)
        `when`(goalRepository.getGoalById(goalId)).thenReturn(flowOf(goal))
        initializeViewModel()

        // Act
        viewModel.getGoalById(goalId)

        // Assert
        verify(goalRepository).getGoalById(goalId)
    }

    @Test
    fun `saveGoal with null id calls repository insert`() = runTest {
        // Arrange
        initializeViewModel()

        // Act
        viewModel.saveGoal(null, "New Car", 200000.0, 50000.0, null, 1)
        advanceUntilIdle()

        // Assert
        verify(goalRepository).insert(capture(goalCaptor))
        val capturedGoal = goalCaptor.value
        assertEquals("New Car", capturedGoal.name)
        assertEquals(200000.0, capturedGoal.targetAmount, 0.0)
        assertEquals(0, capturedGoal.id) // Should be 0 for new item
    }

    @Test
    fun `saveGoal with existing id calls repository update`() = runTest {
        // Arrange
        initializeViewModel()
        val goalId = 5

        // Act
        viewModel.saveGoal(goalId, "Updated Goal", 1500.0, 500.0, null, 2)
        advanceUntilIdle()

        // Assert
        verify(goalRepository).update(capture(goalCaptor))
        val capturedGoal = goalCaptor.value
        assertEquals("Updated Goal", capturedGoal.name)
        assertEquals(1500.0, capturedGoal.targetAmount, 0.0)
        assertEquals(goalId, capturedGoal.id) // ID should match
    }

    @Test
    fun `deleteGoal calls repository delete`() = runTest {
        // Arrange
        initializeViewModel()
        val goalToDelete = Goal(1, "Old Goal", 1000.0, 1000.0, null, 1)

        // Act
        viewModel.deleteGoal(goalToDelete)
        advanceUntilIdle()

        // Assert
        verify(goalRepository).delete(goalToDelete)
    }

    @Test
    fun `saveGoal with null id failure sends error event`() = runTest {
        // Arrange
        val errorMessage = "DB Error"
        `when`(goalRepository.insert(anyObject())).thenThrow(RuntimeException(errorMessage))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.saveGoal(null, "Test", 1.0, 0.0, null, 1)
            advanceUntilIdle()
            assertEquals("Error saving goal: $errorMessage", awaitItem())
        }
    }

    @Test
    fun `saveGoal with existing id failure sends error event`() = runTest {
        // Arrange
        val errorMessage = "DB Error"
        `when`(goalRepository.update(anyObject())).thenThrow(RuntimeException(errorMessage))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.saveGoal(1, "Test", 1.0, 0.0, null, 1)
            advanceUntilIdle()
            assertEquals("Error saving goal: $errorMessage", awaitItem())
        }
    }

    @Test
    fun `deleteGoal success sends success event`() = runTest {
        // Arrange
        val goal = Goal(1, "Test", 1.0, 0.0, null, 1)

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.deleteGoal(goal)
            advanceUntilIdle()

            verify(goalRepository).delete(goal)
            assertEquals("Goal '${goal.name}' deleted.", awaitItem())
        }
    }

    @Test
    fun `deleteGoal failure sends error event`() = runTest {
        // Arrange
        val goal = Goal(1, "Test", 1.0, 0.0, null, 1)
        val errorMessage = "DB Error"
        `when`(goalRepository.delete(anyObject())).thenThrow(RuntimeException(errorMessage))

        // Act & Assert
        viewModel.uiEvent.test {
            viewModel.deleteGoal(goal)
            advanceUntilIdle()

            assertEquals("Error deleting goal: $errorMessage", awaitItem())
        }
    }
}
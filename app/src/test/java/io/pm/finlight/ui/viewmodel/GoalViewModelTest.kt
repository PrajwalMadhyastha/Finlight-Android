// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/GoalViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// FIX (Testing) - Replaced ArgumentCaptor.capture() with the null-safe
// capture() helper to resolve a "capture() must not be null"
// NullPointerException when verifying suspend functions.
// =================================================================================
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
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

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
}
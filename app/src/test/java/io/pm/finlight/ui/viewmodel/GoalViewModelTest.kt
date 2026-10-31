package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.db.entity.GoalContribution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        `when`(goalRepository.getLinkedTransactionCount(anyInt())).thenReturn(flowOf(0))
        `when`(goalRepository.getLinkedTotal(anyInt())).thenReturn(flowOf(0.0))
        `when`(goalRepository.getTotalContributionForGoal(anyInt())).thenReturn(flowOf(0.0))
        initializeViewModel()
    }

    private fun initializeViewModel(initialGoals: List<GoalWithAccountName> = emptyList()) {
        `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(initialGoals))
        viewModel = GoalViewModel(goalRepository)
    }

    @Test
    fun `getGoalById calls repository getGoalById`() =
        runTest {
            // Arrange
            val goalId = 1
            val goal = Goal(goalId, "Vacation", 50000.0, 10000.0, null, 1, null, null, 0)
            `when`(goalRepository.getGoalById(goalId)).thenReturn(flowOf(goal))
            initializeViewModel()

            // Act
            viewModel.getGoalById(goalId)

            // Assert
            verify(goalRepository).getGoalById(goalId)
        }

    @Test
    fun `saveGoal with null id calls repository insert`() =
        runTest {
            // Arrange
            initializeViewModel()

            // Act
            viewModel.saveGoal(null, "New Car", 200000.0, null, 1)
            advanceUntilIdle()

            // Assert
            verify(goalRepository).insert(capture(goalCaptor))
            val capturedGoal = goalCaptor.value
            assertEquals("New Car", capturedGoal.name)
            assertEquals(200000.0, capturedGoal.targetAmount, 0.0)
            assertEquals(0.0, capturedGoal.savedAmount, 0.0) // default offline contribution
            assertEquals(0, capturedGoal.id) // Should be 0 for new item
        }

    @Test
    fun `saveGoal with offline contribution sets savedAmount`() =
        runTest {
            // Arrange
            initializeViewModel()

            // Act
            viewModel.saveGoal(null, "New Car", 200000.0, null, 1, 5000.0)
            advanceUntilIdle()

            // Assert
            verify(goalRepository).insert(capture(goalCaptor))
            val capturedGoal = goalCaptor.value
            assertEquals(5000.0, capturedGoal.savedAmount, 0.0)
        }

    @Test
    fun `saveGoal with existing id calls repository update`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalId = 5

            // Act
            viewModel.saveGoal(goalId, "Updated Goal", 1500.0, null, 2)
            advanceUntilIdle()

            // Assert
            verify(goalRepository).update(capture(goalCaptor))
            val capturedGoal = goalCaptor.value
            assertEquals("Updated Goal", capturedGoal.name)
            assertEquals(1500.0, capturedGoal.targetAmount, 0.0)
            assertEquals(goalId, capturedGoal.id) // ID should match
        }

    @Test
    fun `deleteGoal calls repository delete`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalToDelete = Goal(1, "Old Goal", 1000.0, 1000.0, null, 1, null, null, 0)

            // Act
            viewModel.deleteGoal(goalToDelete)
            advanceUntilIdle()

            // Assert
            verify(goalRepository).delete(goalToDelete)
        }

    @Test
    fun `saveGoal with null id failure sends error event`() =
        runTest {
            // Arrange
            val errorMessage = "DB Error"
            `when`(goalRepository.insert(anyObject())).thenThrow(RuntimeException(errorMessage))

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.saveGoal(null, "Test", 1.0, null, 1)
                advanceUntilIdle()
                assertEquals("Error saving goal: $errorMessage", awaitItem())
            }
        }

    @Test
    fun `saveGoal with existing id failure sends error event`() =
        runTest {
            // Arrange
            val errorMessage = "DB Error"
            `when`(goalRepository.update(anyObject())).thenThrow(RuntimeException(errorMessage))

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.saveGoal(1, "Test", 1.0, null, 1)
                advanceUntilIdle()
                assertEquals("Error saving goal: $errorMessage", awaitItem())
            }
        }

    @Test
    fun `deleteGoal success sends success event`() =
        runTest {
            // Arrange
            val goal = Goal(1, "Test", 1.0, 0.0, null, 1, null, null, 0)

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.deleteGoal(goal)
                advanceUntilIdle()

                verify(goalRepository).delete(goal)
                assertEquals("Goal '${goal.name}' deleted.", awaitItem())
            }
        }

    @Test
    fun `deleteGoal failure sends error event`() =
        runTest {
            // Arrange
            val goal = Goal(1, "Test", 1.0, 0.0, null, 1, null, null, 0)
            val errorMessage = "DB Error"
            `when`(goalRepository.delete(anyObject())).thenThrow(RuntimeException(errorMessage))

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.deleteGoal(goal)
                advanceUntilIdle()

                assertEquals("Error deleting goal: $errorMessage", awaitItem())
            }
        }

    @Test
    fun `linkTransactionToGoal calls repository and sends success event`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalId = 1
            val transactionId = 100

            // Act
            viewModel.uiEvent.test {
                viewModel.linkTransactionToGoal(goalId, transactionId)
                advanceUntilIdle()

                // Assert
                verify(goalRepository).linkTransaction(goalId, transactionId)
                assertEquals("Transaction linked to goal.", awaitItem())
            }
        }

    @Test
    fun `linkTransactionToGoal on failure sends error event`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalId = 1
            val transactionId = 100
            val errorMessage = "Link Error"
            `when`(goalRepository.linkTransaction(goalId, transactionId)).thenThrow(RuntimeException(errorMessage))

            // Act
            viewModel.uiEvent.test {
                viewModel.linkTransactionToGoal(goalId, transactionId)
                advanceUntilIdle()

                // Assert
                assertEquals("Error linking transaction: $errorMessage", awaitItem())
            }
        }

    @Test
    fun `unlinkTransactionFromGoal calls repository and sends success event`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalId = 1
            val transactionId = 100

            // Act
            viewModel.uiEvent.test {
                viewModel.unlinkTransactionFromGoal(goalId, transactionId)
                advanceUntilIdle()

                // Assert
                verify(goalRepository).unlinkTransaction(goalId, transactionId)
                assertEquals("Transaction unlinked from goal.", awaitItem())
            }
        }

    @Test
    fun `unlinkTransactionFromGoal on failure sends error event`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalId = 1
            val transactionId = 100
            val errorMessage = "Unlink Error"
            `when`(goalRepository.unlinkTransaction(goalId, transactionId)).thenThrow(RuntimeException(errorMessage))

            // Act
            viewModel.uiEvent.test {
                viewModel.unlinkTransactionFromGoal(goalId, transactionId)
                advanceUntilIdle()

                // Assert
                assertEquals("Error unlinking transaction: $errorMessage", awaitItem())
            }
        }

    @Test
    fun `getLinkedTotal calls repository and returns flow`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalId = 1
            val expectedTotal = 1500.0
            `when`(goalRepository.getLinkedTotal(goalId)).thenReturn(flowOf(expectedTotal))

            // Act
            val resultFlow = viewModel.getLinkedTotal(goalId)

            // Assert
            resultFlow.test {
                assertEquals(expectedTotal, awaitItem(), 0.0)
                awaitComplete()
            }
            verify(goalRepository).getLinkedTotal(goalId)
        }

    @Test
    fun `getLinkedTransactions calls repository and returns flow`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalId = 2
            val expectedTxns =
                listOf(
                    Transaction(id = 1, description = "Coffee", amount = 50.0, date = 0L, accountId = 1, categoryId = null, notes = null),
                )
            `when`(goalRepository.getLinkedTransactions(goalId)).thenReturn(flowOf(expectedTxns))

            // Act
            val resultFlow = viewModel.getLinkedTransactions(goalId)

            // Assert
            resultFlow.test {
                assertEquals(expectedTxns, awaitItem())
                awaitComplete()
            }
            verify(goalRepository).getLinkedTransactions(goalId)
        }

    @Test
    fun `getLinkedTransactionCount calls repository and returns flow`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalId = 3
            `when`(goalRepository.getLinkedTransactionCount(goalId)).thenReturn(flowOf(5))

            // Act
            val resultFlow = viewModel.getLinkedTransactionCount(goalId)

            // Assert
            resultFlow.test {
                assertEquals(5, awaitItem())
                awaitComplete()
            }
            verify(goalRepository).getLinkedTransactionCount(goalId)
        }

    @Test
    fun `getLinkedTransactionIds calls repository and returns flow`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalId = 4
            val expectedIds = listOf(10, 20, 30)
            `when`(goalRepository.getLinkedTransactionIds(goalId)).thenReturn(flowOf(expectedIds))

            // Act
            val resultFlow = viewModel.getLinkedTransactionIds(goalId)

            // Assert
            resultFlow.test {
                assertEquals(expectedIds, awaitItem())
                awaitComplete()
            }
            verify(goalRepository).getLinkedTransactionIds(goalId)
        }

    @Test
    fun `getActiveGoalsSnapshot calls repository and returns list`() =
        runTest {
            // Arrange
            initializeViewModel()
            val expectedGoals = listOf(Goal(id = 1, name = "Emergency Fund", targetAmount = 50000.0, targetDate = null, accountId = 1))
            `when`(goalRepository.getActiveGoalsSnapshot()).thenReturn(expectedGoals)

            // Act
            val result = viewModel.getActiveGoalsSnapshot()

            // Assert
            assertEquals(expectedGoals, result)
            verify(goalRepository).getActiveGoalsSnapshot()
        }

    @Test
    fun `getRecentTransactions calls repository and returns flow`() =
        runTest {
            // Arrange
            initializeViewModel()
            val start = 1000L
            val end = 2000L
            val expectedTxns =
                listOf(
                    Transaction(id = 5, description = "Lunch", amount = 150.0, date = 1500L, accountId = 1, categoryId = null, notes = null),
                )
            `when`(goalRepository.getRecentTransactions(start, end)).thenReturn(flowOf(expectedTxns))

            // Act
            val resultFlow = viewModel.getRecentTransactions(start, end)

            // Assert
            resultFlow.test {
                assertEquals(expectedTxns, awaitItem())
                awaitComplete()
            }
            verify(goalRepository).getRecentTransactions(start, end)
        }

    // --- Manual Contributions ---

    @Test
    fun `getContributionsForGoal calls repository and returns flow`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalId = 1
            val expectedContributions = listOf(GoalContribution(id = 1, goalId = goalId, amount = 100.0, date = 1000L, description = "Test"))
            `when`(goalRepository.getContributionsForGoal(goalId)).thenReturn(flowOf(expectedContributions))

            // Act
            val resultFlow = viewModel.getContributionsForGoal(goalId)

            // Assert
            resultFlow.test {
                assertEquals(expectedContributions, awaitItem())
                awaitComplete()
            }
            verify(goalRepository).getContributionsForGoal(goalId)
        }

    @Test
    fun `getTotalContributionForGoal calls repository and returns flow`() =
        runTest {
            // Arrange
            initializeViewModel()
            val goalId = 1
            val expectedTotal = 150.0
            `when`(goalRepository.getTotalContributionForGoal(goalId)).thenReturn(flowOf(expectedTotal))

            // Act
            val resultFlow = viewModel.getTotalContributionForGoal(goalId)

            // Assert
            resultFlow.test {
                assertEquals(expectedTotal, awaitItem(), 0.0)
                awaitComplete()
            }
            verify(goalRepository).getTotalContributionForGoal(goalId)
        }

    @Test
    fun `insertContribution success sends success event`() =
        runTest {
            // Arrange
            initializeViewModel()
            val contribution = GoalContribution(goalId = 1, amount = 100.0, date = 1000L, description = "Test")

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.insertContribution(contribution)
                advanceUntilIdle()

                verify(goalRepository).insertContribution(contribution)
                assertEquals("Manual contribution added.", awaitItem())
            }
        }

    @Test
    fun `insertContribution failure sends error event`() =
        runTest {
            // Arrange
            initializeViewModel()
            val contribution = GoalContribution(goalId = 1, amount = 100.0, date = 1000L, description = "Test")
            val errorMessage = "DB Error"
            `when`(goalRepository.insertContribution(anyObject())).thenThrow(RuntimeException(errorMessage))

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.insertContribution(contribution)
                advanceUntilIdle()

                assertEquals("Error adding contribution: $errorMessage", awaitItem())
            }
        }

    @Test
    fun `updateContribution success sends success event`() =
        runTest {
            // Arrange
            initializeViewModel()
            val contribution = GoalContribution(id = 1, goalId = 1, amount = 100.0, date = 1000L, description = "Test")

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.updateContribution(contribution)
                advanceUntilIdle()

                verify(goalRepository).updateContribution(contribution)
                assertEquals("Manual contribution updated.", awaitItem())
            }
        }

    @Test
    fun `updateContribution failure sends error event`() =
        runTest {
            // Arrange
            initializeViewModel()
            val contribution = GoalContribution(id = 1, goalId = 1, amount = 100.0, date = 1000L, description = "Test")
            val errorMessage = "DB Error"
            `when`(goalRepository.updateContribution(anyObject())).thenThrow(RuntimeException(errorMessage))

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.updateContribution(contribution)
                advanceUntilIdle()

                assertEquals("Error updating contribution: $errorMessage", awaitItem())
            }
        }

    @Test
    fun `deleteContribution success sends success event`() =
        runTest {
            // Arrange
            initializeViewModel()
            val contribution = GoalContribution(id = 1, goalId = 1, amount = 100.0, date = 1000L, description = "Test")

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.deleteContribution(contribution)
                advanceUntilIdle()

                verify(goalRepository).deleteContribution(contribution)
                assertEquals("Manual contribution deleted.", awaitItem())
            }
        }

    @Test
    fun `deleteContribution failure sends error event`() =
        runTest {
            // Arrange
            initializeViewModel()
            val contribution = GoalContribution(id = 1, goalId = 1, amount = 100.0, date = 1000L, description = "Test")
            val errorMessage = "DB Error"
            `when`(goalRepository.deleteContribution(anyObject())).thenThrow(RuntimeException(errorMessage))

            // Act & Assert
            viewModel.uiEvent.test {
                viewModel.deleteContribution(contribution)
                advanceUntilIdle()

                assertEquals("Error deleting contribution: $errorMessage", awaitItem())
            }
        }

    // --- goalsWithProgress tests ---

    @Test
    fun `goalsWithProgress emits empty list when repository has no goals`() =
        runTest {
            // Arrange
            initializeViewModel(emptyList())

            // Act & Assert
            viewModel.goalsWithProgress.test {
                assertEquals(emptyList<GoalProgressState>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `goalsWithProgress computes progress for offline base savings only`() =
        runTest {
            // Arrange
            val goal = GoalWithAccountName(1, "Vacation", 1000.0, 250.0, null, 1, "Savings", null, null, 0)
            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goal)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(flowOf(0.0))
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(flowOf(0.0))
            viewModel = GoalViewModel(goalRepository)

            // Act & Assert
            viewModel.goalsWithProgress.test {
                val list = awaitItem()
                assertEquals(1, list.size)
                val state = list[0]
                assertEquals(goal, state.goalWithAccount)
                assertEquals(0.0, state.linkedTotal, 0.0)
                assertEquals(250.0, state.offlineTotal, 0.0)
                assertEquals(0.0, state.manualContributionsTotal, 0.0)
                assertEquals(250.0, state.totalSaved, 0.0)
                assertEquals(0.25f, state.progressPercentage, 0.001f)
                assertFalse(state.isCompleted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `goalsWithProgress computes progress for linked transactions only`() =
        runTest {
            // Arrange
            val goal = GoalWithAccountName(1, "Vacation", 1000.0, 0.0, null, 1, "Savings", null, null, 0)
            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goal)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(flowOf(400.0))
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(flowOf(0.0))
            viewModel = GoalViewModel(goalRepository)

            // Act & Assert
            viewModel.goalsWithProgress.test {
                val list = awaitItem()
                assertEquals(1, list.size)
                val state = list[0]
                assertEquals(400.0, state.linkedTotal, 0.0)
                assertEquals(0.0, state.offlineTotal, 0.0)
                assertEquals(0.0, state.manualContributionsTotal, 0.0)
                assertEquals(400.0, state.totalSaved, 0.0)
                assertEquals(0.4f, state.progressPercentage, 0.001f)
                assertFalse(state.isCompleted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `goalsWithProgress computes progress for manual contributions only`() =
        runTest {
            // Arrange
            val goal = GoalWithAccountName(1, "Emergency Fund", 2000.0, 0.0, null, 1, "Savings", null, null, 0)
            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goal)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(flowOf(0.0))
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(flowOf(600.0))
            viewModel = GoalViewModel(goalRepository)

            // Act & Assert
            viewModel.goalsWithProgress.test {
                val list = awaitItem()
                assertEquals(1, list.size)
                val state = list[0]
                assertEquals(0.0, state.linkedTotal, 0.0)
                assertEquals(0.0, state.offlineTotal, 0.0)
                assertEquals(600.0, state.manualContributionsTotal, 0.0)
                assertEquals(600.0, state.totalSaved, 0.0)
                assertEquals(0.3f, state.progressPercentage, 0.001f)
                assertFalse(state.isCompleted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `goalsWithProgress computes progress for mixed contributions without double counting`() =
        runTest {
            // Arrange
            val goal = GoalWithAccountName(1, "Laptop", 1000.0, 200.0, null, 1, "Savings", null, null, 0)
            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goal)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(flowOf(500.0))
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(flowOf(300.0))
            viewModel = GoalViewModel(goalRepository)

            // Act & Assert
            viewModel.goalsWithProgress.test {
                val list = awaitItem()
                assertEquals(1, list.size)
                val state = list[0]
                assertEquals(500.0, state.linkedTotal, 0.0)
                assertEquals(200.0, state.offlineTotal, 0.0)
                assertEquals(300.0, state.manualContributionsTotal, 0.0)
                assertEquals(1000.0, state.totalSaved, 0.0)
                assertEquals(1.0f, state.progressPercentage, 0.001f)
                assertTrue(state.isCompleted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `goalsWithProgress clamps progress to 1f and marks completed when surplus exists`() =
        runTest {
            // Arrange
            val goal = GoalWithAccountName(1, "Goal", 500.0, 300.0, null, 1, "Savings", null, null, 0)
            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goal)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(flowOf(400.0))
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(flowOf(100.0))
            viewModel = GoalViewModel(goalRepository)

            // Act & Assert
            viewModel.goalsWithProgress.test {
                val list = awaitItem()
                assertEquals(1, list.size)
                val state = list[0]
                assertEquals(800.0, state.totalSaved, 0.0)
                assertEquals(1.0f, state.progressPercentage, 0.001f)
                assertTrue(state.isCompleted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `goalsWithProgress clamps progress to 0f when totalSaved is negative`() =
        runTest {
            // Arrange
            val goal = GoalWithAccountName(1, "Goal", 500.0, -100.0, null, 1, "Savings", null, null, 0)
            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goal)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(flowOf(0.0))
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(flowOf(0.0))
            viewModel = GoalViewModel(goalRepository)

            // Act & Assert
            viewModel.goalsWithProgress.test {
                val list = awaitItem()
                assertEquals(1, list.size)
                val state = list[0]
                assertEquals(-100.0, state.totalSaved, 0.0)
                assertEquals(0.0f, state.progressPercentage, 0.001f)
                assertFalse(state.isCompleted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `goalsWithProgress returns 0f progress and incomplete when targetAmount is zero or negative`() =
        runTest {
            // Arrange
            val goalZero = GoalWithAccountName(1, "Zero Target", 0.0, 100.0, null, 1, "Savings", null, null, 0)
            val goalNegative = GoalWithAccountName(2, "Negative Target", -50.0, 100.0, null, 1, "Savings", null, null, 0)
            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goalZero, goalNegative)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(flowOf(50.0))
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(flowOf(50.0))
            `when`(goalRepository.getLinkedTotal(2)).thenReturn(flowOf(50.0))
            `when`(goalRepository.getTotalContributionForGoal(2)).thenReturn(flowOf(50.0))
            viewModel = GoalViewModel(goalRepository)

            // Act & Assert
            viewModel.goalsWithProgress.test {
                val list = awaitItem()
                assertEquals(2, list.size)
                assertEquals(0.0f, list[0].progressPercentage, 0.001f)
                assertFalse(list[0].isCompleted)
                assertEquals(0.0f, list[1].progressPercentage, 0.001f)
                assertFalse(list[1].isCompleted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `goalsWithProgress dynamically updates when linked or manual flows emit new values`() =
        runTest {
            // Arrange
            val goal = GoalWithAccountName(1, "Bike", 1000.0, 100.0, null, 1, "Savings", null, null, 0)
            val linkedFlow = MutableStateFlow(200.0)
            val manualFlow = MutableStateFlow(100.0)

            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goal)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(linkedFlow)
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(manualFlow)
            viewModel = GoalViewModel(goalRepository)

            // Act & Assert
            viewModel.goalsWithProgress.test {
                // Initial: 100 offline + 200 linked + 100 manual = 400 (40%)
                val initial = awaitItem()
                assertEquals(400.0, initial[0].totalSaved, 0.0)
                assertEquals(0.4f, initial[0].progressPercentage, 0.001f)
                assertFalse(initial[0].isCompleted)

                // Update linked total to 500: total = 100 + 500 + 100 = 700 (70%)
                linkedFlow.value = 500.0
                val updatedLinked = awaitItem()
                assertEquals(700.0, updatedLinked[0].totalSaved, 0.0)
                assertEquals(0.7f, updatedLinked[0].progressPercentage, 0.001f)
                assertFalse(updatedLinked[0].isCompleted)

                // Update manual total to 400: total = 100 + 500 + 400 = 1000 (100%)
                manualFlow.value = 400.0
                val completed = awaitItem()
                assertEquals(1000.0, completed[0].totalSaved, 0.0)
                assertEquals(1.0f, completed[0].progressPercentage, 0.001f)
                assertTrue(completed[0].isCompleted)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `GoalProgressState create helper correctly computes and clamps values`() {
        val goal = GoalWithAccountName(1, "Trip", 200.0, 50.0, null, 1, "Savings", null, null, 0)

        val stateNormal = GoalProgressState.create(goal, linkedTotal = 50.0, manualContributionsTotal = 20.0, linkedTransactionCount = 3)
        assertEquals(50.0, stateNormal.offlineTotal, 0.0)
        assertEquals(50.0, stateNormal.linkedTotal, 0.0)
        assertEquals(20.0, stateNormal.manualContributionsTotal, 0.0)
        assertEquals(70.0, stateNormal.totalOfflineSavings, 0.0)
        assertEquals(120.0, stateNormal.totalSaved, 0.0)
        assertEquals(0.6f, stateNormal.progressPercentage, 0.001f)
        assertFalse(stateNormal.isCompleted)
        assertEquals(3, stateNormal.linkedTransactionCount)

        val stateClamped = GoalProgressState.create(goal, linkedTotal = 200.0, manualContributionsTotal = 50.0)
        assertEquals(100.0, stateClamped.totalOfflineSavings, 0.0)
        assertEquals(300.0, stateClamped.totalSaved, 0.0)
        assertEquals(1.0f, stateClamped.progressPercentage, 0.001f)
        assertTrue(stateClamped.isCompleted)
        assertEquals(0, stateClamped.linkedTransactionCount)
    }

    @Test
    fun `goalsWithProgress aggregates linked transaction count into GoalProgressState`() =
        runTest {
            val goal = GoalWithAccountName(1, "New Laptop", 50000.0, 5000.0, null, 1, "Savings", null, null, 0)
            val txnCountFlow = MutableStateFlow(4)
            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goal)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(flowOf(10000.0))
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(flowOf(2000.0))
            `when`(goalRepository.getLinkedTransactionCount(1)).thenReturn(txnCountFlow)
            viewModel = GoalViewModel(goalRepository)

            viewModel.goalsWithProgress.test {
                val item = awaitItem()
                assertEquals(1, item.size)
                assertEquals(4, item[0].linkedTransactionCount)

                txnCountFlow.value = 7
                val updated = awaitItem()
                assertEquals(7, updated[0].linkedTransactionCount)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getGoalProgress emits GoalProgressState for existing goal and null for non-existent goal`() =
        runTest {
            // Arrange
            val goal = GoalWithAccountName(1, "Vacation", 1000.0, 200.0, null, 1, "Savings", null, null, 0)
            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goal)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(flowOf(300.0))
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(flowOf(100.0))
            viewModel = GoalViewModel(goalRepository)

            // Act & Assert - existing goal
            viewModel.getGoalProgress(1).test {
                val progress = awaitItem()
                assertTrue(progress != null)
                assertEquals(1, progress!!.goalWithAccount.id)
                assertEquals(600.0, progress.totalSaved, 0.0)
                assertEquals(0.6f, progress.progressPercentage, 0.001f)
                assertFalse(progress.isCompleted)
                cancelAndIgnoreRemainingEvents()
            }

            // Act & Assert - non-existent goal
            viewModel.getGoalProgress(999).test {
                val progress = awaitItem()
                assertNull(progress)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getGoalProgress does not emit duplicate values when other goals change`() =
        runTest {
            // Arrange
            val goal1 = GoalWithAccountName(1, "Goal 1", 1000.0, 100.0, null, 1, "Savings", null, null, 0)
            val goal2 = GoalWithAccountName(2, "Goal 2", 2000.0, 200.0, null, 1, "Savings", null, null, 0)
            val linkedFlow1 = MutableStateFlow(100.0)
            val manualFlow1 = MutableStateFlow(50.0)
            val linkedFlow2 = MutableStateFlow(300.0)
            val manualFlow2 = MutableStateFlow(100.0)

            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goal1, goal2)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(linkedFlow1)
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(manualFlow1)
            `when`(goalRepository.getLinkedTotal(2)).thenReturn(linkedFlow2)
            `when`(goalRepository.getTotalContributionForGoal(2)).thenReturn(manualFlow2)
            viewModel = GoalViewModel(goalRepository)

            // Act & Assert
            viewModel.getGoalProgress(1).test {
                val initial = awaitItem()
                assertNotNull(initial)
                assertEquals(1, initial!!.goalWithAccount.id)
                assertEquals(250.0, initial.totalSaved, 0.0)

                // Updating Goal 2 changes goalsWithProgress, but getGoalProgress(1) should not emit because it is distinctUntilChanged
                linkedFlow2.value = 500.0
                expectNoEvents()

                // Updating Goal 1 emits a new value
                linkedFlow1.value = 350.0
                val updated = awaitItem()
                assertNotNull(updated)
                assertEquals(500.0, updated!!.totalSaved, 0.0)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `goalsWithProgress dynamically updates multi-goal list when individual goals update`() =
        runTest {
            // Arrange
            val goal1 = GoalWithAccountName(1, "Goal 1", 1000.0, 100.0, null, 1, "Savings", null, null, 0)
            val goal2 = GoalWithAccountName(2, "Goal 2", 2000.0, 200.0, null, 1, "Savings", null, null, 0)
            val linkedFlow1 = MutableStateFlow(100.0)
            val manualFlow1 = MutableStateFlow(50.0)
            val linkedFlow2 = MutableStateFlow(300.0)
            val manualFlow2 = MutableStateFlow(100.0)

            `when`(goalRepository.getAllGoalsWithAccountName()).thenReturn(flowOf(listOf(goal1, goal2)))
            `when`(goalRepository.getLinkedTotal(1)).thenReturn(linkedFlow1)
            `when`(goalRepository.getTotalContributionForGoal(1)).thenReturn(manualFlow1)
            `when`(goalRepository.getLinkedTotal(2)).thenReturn(linkedFlow2)
            `when`(goalRepository.getTotalContributionForGoal(2)).thenReturn(manualFlow2)
            viewModel = GoalViewModel(goalRepository)

            // Act & Assert
            viewModel.goalsWithProgress.test {
                // Initial emission:
                // Goal 1: 100 offline + 100 linked + 50 manual = 250 (25%)
                // Goal 2: 200 offline + 300 linked + 100 manual = 600 (30%)
                val initial = awaitItem()
                assertEquals(2, initial.size)
                assertEquals(250.0, initial[0].totalSaved, 0.0)
                assertEquals(0.25f, initial[0].progressPercentage, 0.001f)
                assertEquals(600.0, initial[1].totalSaved, 0.0)
                assertEquals(0.30f, initial[1].progressPercentage, 0.001f)

                // Update Goal 1 linked total: Goal 1 updates, Goal 2 remains constant
                linkedFlow1.value = 350.0
                val update1 = awaitItem()
                assertEquals(500.0, update1[0].totalSaved, 0.0)
                assertEquals(0.50f, update1[0].progressPercentage, 0.001f)
                assertEquals(600.0, update1[1].totalSaved, 0.0)
                assertEquals(0.30f, update1[1].progressPercentage, 0.001f)

                // Update Goal 2 manual total: Goal 2 updates, Goal 1 remains constant
                manualFlow2.value = 500.0
                val update2 = awaitItem()
                assertEquals(500.0, update2[0].totalSaved, 0.0)
                assertEquals(0.50f, update2[0].progressPercentage, 0.001f)
                assertEquals(1000.0, update2[1].totalSaved, 0.0)
                assertEquals(0.50f, update2[1].progressPercentage, 0.001f)

                cancelAndIgnoreRemainingEvents()
            }
        }
}

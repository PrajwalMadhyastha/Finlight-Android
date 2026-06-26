package io.pm.finlight.ui.viewmodel

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.*
import io.pm.finlight.data.db.entity.GoalContribution
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
    fun `allGoals flow emits goals from repository`() =
        runTest {
            // Arrange
            val goals =
                listOf(
                    GoalWithAccountName(1, "Vacation", 50000.0, 10000.0, null, 1, "Savings", null, null, 0),
                )
            initializeViewModel(goals)

            // Assert
            viewModel.allGoals.test {
                assertEquals(goals, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
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
}

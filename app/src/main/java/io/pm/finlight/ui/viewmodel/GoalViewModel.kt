// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/GoalViewModel.kt
// REASON: REFACTOR (Testing) - The ViewModel now uses constructor dependency
// injection for GoalRepository and extends ViewModel instead of AndroidViewModel.
// This decouples it from the Application context, making it fully unit-testable.
// REASON: FEATURE (Error Handling) - Added a UI event channel and wrapped all
// data modification methods (`saveGoal`, `deleteGoal`) in try-catch blocks.
// This ensures repository exceptions are handled gracefully and communicated to
// the user, enhancing app stability.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GoalViewModel(private val goalRepository: GoalRepository) : ViewModel() {

    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()

    val allGoals: StateFlow<List<GoalWithAccountName>> = goalRepository.getAllGoalsWithAccountName()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun getGoalById(id: Int): Flow<Goal?> {
        return goalRepository.getGoalById(id)
    }

    fun saveGoal(
        id: Int?,
        name: String,
        targetAmount: Double,
        savedAmount: Double,
        targetDate: Long?,
        accountId: Int
    ) {
        viewModelScope.launch {
            try {
                val goal = Goal(
                    id = id ?: 0,
                    name = name,
                    targetAmount = targetAmount,
                    savedAmount = savedAmount,
                    targetDate = targetDate,
                    accountId = accountId
                )
                if (id == null) {
                    goalRepository.insert(goal)
                    _uiEvent.send("Goal '${goal.name}' created.")
                } else {
                    goalRepository.update(goal)
                    _uiEvent.send("Goal '${goal.name}' updated.")
                }
            } catch (e: Exception) {
                _uiEvent.send("Error saving goal: ${e.message}")
            }
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            try {
                goalRepository.delete(goal)
                _uiEvent.send("Goal '${goal.name}' deleted.")
            } catch (e: Exception) {
                _uiEvent.send("Error deleting goal: ${e.message}")
            }
        }
    }
}
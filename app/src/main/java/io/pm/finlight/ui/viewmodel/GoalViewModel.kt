// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/GoalViewModel.kt
// REASON: REFACTOR (Testing) - The ViewModel now uses constructor dependency
// injection for GoalRepository and extends ViewModel instead of AndroidViewModel.
// This decouples it from the Application context, making it fully unit-testable.
// =================================================================================
package io.pm.finlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GoalViewModel(private val goalRepository: GoalRepository) : ViewModel() {

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
            } else {
                goalRepository.update(goal)
            }
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            goalRepository.delete(goal)
        }
    }
}
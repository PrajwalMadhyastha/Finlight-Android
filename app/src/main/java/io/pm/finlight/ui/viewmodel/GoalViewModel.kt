package io.pm.finlight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pm.finlight.Goal
import io.pm.finlight.GoalWithAccountName
import io.pm.finlight.IGoalRepository
import io.pm.finlight.Transaction
import io.pm.finlight.data.db.entity.GoalContribution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalProgressState(
    val goalWithAccount: GoalWithAccountName,
    val linkedTotal: Double,
    val offlineTotal: Double,
    val manualContributionsTotal: Double,
    val totalSaved: Double,
    val progressPercentage: Float,
    val isCompleted: Boolean,
) {
    companion object {
        fun create(
            goalWithAccount: GoalWithAccountName,
            linkedTotal: Double,
            manualContributionsTotal: Double,
            offlineTotal: Double = goalWithAccount.savedAmount,
        ): GoalProgressState {
            val totalSaved = offlineTotal + linkedTotal + manualContributionsTotal
            val progressPercentage =
                if (goalWithAccount.targetAmount > 0.0) {
                    (totalSaved / goalWithAccount.targetAmount).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
            return GoalProgressState(
                goalWithAccount = goalWithAccount,
                linkedTotal = linkedTotal,
                offlineTotal = offlineTotal,
                manualContributionsTotal = manualContributionsTotal,
                totalSaved = totalSaved,
                progressPercentage = progressPercentage,
                isCompleted = progressPercentage >= 1f,
            )
        }
    }
}

class GoalViewModel(private val goalRepository: IGoalRepository) : ViewModel() {
    private val _uiEvent = Channel<String>(Channel.UNLIMITED)
    val uiEvent = _uiEvent.receiveAsFlow()

    val allGoals: StateFlow<List<GoalWithAccountName>> =
        goalRepository.getAllGoalsWithAccountName()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val goalsWithProgress: StateFlow<List<GoalProgressState>> =
        goalRepository.getAllGoalsWithAccountName()
            .flatMapLatest { goals ->
                if (goals.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val flows: List<Flow<GoalProgressState>> =
                        goals.map { goal ->
                            combine(
                                goalRepository.getLinkedTotal(goal.id),
                                goalRepository.getTotalContributionForGoal(goal.id),
                            ) { linkedTotal, manualContributionsTotal ->
                                GoalProgressState.create(
                                    goalWithAccount = goal,
                                    linkedTotal = linkedTotal,
                                    manualContributionsTotal = manualContributionsTotal,
                                )
                            }
                        }
                    combine(flows) { it.toList() }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    val activeGoals: StateFlow<List<Goal>> =
        goalRepository.getActiveGoals()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    fun getGoalById(id: Int): Flow<Goal?> {
        return goalRepository.getGoalById(id)
    }

    fun getLinkedTotal(goalId: Int): Flow<Double> = goalRepository.getLinkedTotal(goalId)

    fun getLinkedTransactions(goalId: Int): Flow<List<Transaction>> = goalRepository.getLinkedTransactions(goalId)

    fun getLinkedTransactionCount(goalId: Int): Flow<Int> = goalRepository.getLinkedTransactionCount(goalId)

    fun getLinkedTransactionIds(goalId: Int): Flow<List<Int>> = goalRepository.getLinkedTransactionIds(goalId)

    suspend fun getActiveGoalsSnapshot(): List<Goal> = goalRepository.getActiveGoalsSnapshot()

    fun getRecentTransactions(
        startTime: Long,
        endTime: Long
    ): Flow<List<Transaction>> {
        // Expose recent transactions for the linking picker.
        // In a real app we might inject TransactionRepository, but GoalRepository has access to DB.
        return goalRepository.getRecentTransactions(startTime, endTime)
    }

    fun saveGoal(
        id: Int?,
        name: String,
        targetAmount: Double,
        targetDate: Long?,
        accountId: Int,
        offlineContribution: Double = 0.0,
        notes: String? = null,
        iconEmoji: String? = null,
        priority: Int = 0,
    ) {
        viewModelScope.launch {
            try {
                val goal =
                    Goal(
                        id = id ?: 0,
                        name = name,
                        targetAmount = targetAmount,
                        savedAmount = offlineContribution,
                        targetDate = targetDate,
                        accountId = accountId,
                        notes = notes,
                        iconEmoji = iconEmoji,
                        priority = priority,
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

    fun linkTransactionToGoal(
        goalId: Int,
        transactionId: Int
    ) {
        viewModelScope.launch {
            try {
                goalRepository.linkTransaction(goalId, transactionId)
                _uiEvent.send("Transaction linked to goal.")
            } catch (e: Exception) {
                _uiEvent.send("Error linking transaction: ${e.message}")
            }
        }
    }

    fun unlinkTransactionFromGoal(
        goalId: Int,
        transactionId: Int
    ) {
        viewModelScope.launch {
            try {
                goalRepository.unlinkTransaction(goalId, transactionId)
                _uiEvent.send("Transaction unlinked from goal.")
            } catch (e: Exception) {
                _uiEvent.send("Error unlinking transaction: ${e.message}")
            }
        }
    }

    // --- Manual Contributions ---

    fun getContributionsForGoal(goalId: Int): Flow<List<GoalContribution>> = goalRepository.getContributionsForGoal(goalId)

    fun getTotalContributionForGoal(goalId: Int): Flow<Double> = goalRepository.getTotalContributionForGoal(goalId)

    fun insertContribution(contribution: GoalContribution) {
        viewModelScope.launch {
            try {
                goalRepository.insertContribution(contribution)
                _uiEvent.send("Manual contribution added.")
            } catch (e: Exception) {
                _uiEvent.send("Error adding contribution: ${e.message}")
            }
        }
    }

    fun updateContribution(contribution: GoalContribution) {
        viewModelScope.launch {
            try {
                goalRepository.updateContribution(contribution)
                _uiEvent.send("Manual contribution updated.")
            } catch (e: Exception) {
                _uiEvent.send("Error updating contribution: ${e.message}")
            }
        }
    }

    fun deleteContribution(contribution: GoalContribution) {
        viewModelScope.launch {
            try {
                goalRepository.deleteContribution(contribution)
                _uiEvent.send("Manual contribution deleted.")
            } catch (e: Exception) {
                _uiEvent.send("Error deleting contribution: ${e.message}")
            }
        }
    }
}

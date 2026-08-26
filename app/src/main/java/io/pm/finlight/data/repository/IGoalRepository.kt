package io.pm.finlight

import io.pm.finlight.data.db.entity.GoalContribution
import kotlinx.coroutines.flow.Flow

interface IGoalRepository {
    fun getAllGoalsWithAccountName(): Flow<List<GoalWithAccountName>>

    fun getGoalById(id: Int): Flow<Goal?>

    fun getActiveGoals(): Flow<List<Goal>>

    suspend fun getActiveGoalsSnapshot(): List<Goal>

    fun getRecentTransactions(
        startTime: Long,
        endTime: Long,
    ): Flow<List<Transaction>>

    suspend fun insert(goal: Goal)

    suspend fun update(goal: Goal)

    suspend fun delete(goal: Goal)

    suspend fun linkTransaction(
        goalId: Int,
        transactionId: Int,
    )

    suspend fun unlinkTransaction(
        goalId: Int,
        transactionId: Int,
    )

    fun getLinkedTotal(goalId: Int): Flow<Double>

    fun getLinkedTransactions(goalId: Int): Flow<List<Transaction>>

    fun getLinkedTransactionCount(goalId: Int): Flow<Int>

    fun getLinkedTransactionIds(goalId: Int): Flow<List<Int>>

    fun getContributionsForGoal(goalId: Int): Flow<List<GoalContribution>>

    fun getTotalContributionForGoal(goalId: Int): Flow<Double>

    suspend fun insertContribution(contribution: GoalContribution)

    suspend fun updateContribution(contribution: GoalContribution)

    suspend fun deleteContribution(contribution: GoalContribution)
}

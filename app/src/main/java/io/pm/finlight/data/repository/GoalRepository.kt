// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/GoalRepository.kt
// REASON: FEATURE (Issue #104) - Expanded with transaction linking support.
// The repository now wraps both GoalDao and GoalTransactionLinkDao, providing
// a clean API for linking/unlinking transactions and computing dynamic progress.
// =================================================================================
package io.pm.finlight

import io.pm.finlight.data.db.dao.GoalTransactionLinkDao
import io.pm.finlight.data.db.dao.GoalContributionDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.entity.GoalTransactionLink
import io.pm.finlight.data.db.entity.GoalContribution
import kotlinx.coroutines.flow.Flow

class GoalRepository(
    private val goalDao: GoalDao,
    private val linkDao: GoalTransactionLinkDao,
    private val transactionQueryDao: TransactionQueryDao,
    private val contributionDao: GoalContributionDao,
) : IGoalRepository {
    override fun getAllGoalsWithAccountName(): Flow<List<GoalWithAccountName>> = goalDao.getAllGoalsWithAccountName()

    override fun getGoalById(id: Int): Flow<Goal?> = goalDao.getGoalById(id)

    override fun getActiveGoals(): Flow<List<Goal>> = goalDao.getActiveGoals()

    override suspend fun getActiveGoalsSnapshot(): List<Goal> {
        val flowList = goalDao.getActiveGoalsSnapshot()
        return flowList
    }

    override fun getRecentTransactions(
        startTime: Long,
        endTime: Long
    ): Flow<List<Transaction>> {
        return transactionQueryDao.getAllTransactionsForRange(startTime, endTime)
    }

    override suspend fun insert(goal: Goal) {
        goalDao.insert(goal)
    }

    override suspend fun update(goal: Goal) {
        goalDao.update(goal)
    }

    override suspend fun delete(goal: Goal) {
        goalDao.delete(goal)
    }

    // --- Transaction Linking ---

    override suspend fun linkTransaction(
        goalId: Int,
        transactionId: Int
    ) {
        linkDao.insertLink(GoalTransactionLink(goalId = goalId, transactionId = transactionId))
    }

    override suspend fun unlinkTransaction(
        goalId: Int,
        transactionId: Int
    ) {
        linkDao.deleteLink(goalId, transactionId)
    }

    override fun getLinkedTotal(goalId: Int): Flow<Double> = linkDao.getLinkedTransactionsTotal(goalId)

    override fun getLinkedTransactions(goalId: Int): Flow<List<Transaction>> = linkDao.getLinkedTransactions(goalId)

    override fun getLinkedTransactionCount(goalId: Int): Flow<Int> = linkDao.getLinkedTransactionCount(goalId)

    override fun getLinkedTransactionIds(goalId: Int): Flow<List<Int>> = linkDao.getLinkedTransactionIds(goalId)

    // --- Manual Contributions ---

    override fun getContributionsForGoal(goalId: Int): Flow<List<GoalContribution>> = contributionDao.getContributionsForGoal(goalId)

    override fun getTotalContributionForGoal(goalId: Int): Flow<Double> = contributionDao.getTotalContributionForGoal(goalId)

    override suspend fun insertContribution(contribution: GoalContribution) = contributionDao.insertContribution(contribution)

    override suspend fun updateContribution(contribution: GoalContribution) = contributionDao.updateContribution(contribution)

    override suspend fun deleteContribution(contribution: GoalContribution) = contributionDao.deleteContribution(contribution)
}

// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/GoalRepository.kt
// REASON: NEW FILE - Creates a repository to abstract the Goal data source,
// providing a clean API for the ViewModel to interact with the DAO.
// =================================================================================
package io.pm.finlight

import kotlinx.coroutines.flow.Flow

class GoalRepository(private val goalDao: GoalDao) {
    fun getAllGoalsWithAccountName(): Flow<List<GoalWithAccountName>> = goalDao.getAllGoalsWithAccountName()

    fun getGoalById(id: Int): Flow<Goal?> = goalDao.getGoalById(id)

    suspend fun insert(goal: Goal) {
        goalDao.insert(goal)
    }

    suspend fun update(goal: Goal) {
        goalDao.update(goal)
    }

    suspend fun delete(goal: Goal) {
        goalDao.delete(goal)
    }
}

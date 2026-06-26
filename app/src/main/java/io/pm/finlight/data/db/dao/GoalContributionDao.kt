package io.pm.finlight.data.db.dao

import androidx.room.*
import io.pm.finlight.data.db.entity.GoalContribution
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalContributionDao {
    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY date DESC")
    fun getContributionsForGoal(goalId: Int): Flow<List<GoalContribution>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM goal_contributions WHERE goalId = :goalId")
    fun getTotalContributionForGoal(goalId: Int): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContribution(contribution: GoalContribution)

    @Update
    suspend fun updateContribution(contribution: GoalContribution)

    @Delete
    suspend fun deleteContribution(contribution: GoalContribution)
}

// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/GoalSurplusWorker.kt
// REASON: FEATURE (Issue #104) - Smart Allocation Prompts
// A monthly worker that checks for budget surplus from the previous month
// and sends a nudge to allocate it to active savings goals.
// =================================================================================
package io.pm.finlight.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.SettingsRepository
import io.pm.finlight.Goal
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

class GoalSurplusWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val logTag = "GoalSurplusWorker"
        Log.d(logTag, "Starting GoalSurplusWorker")

        val settingsRepository = SettingsRepository(applicationContext)

        // 1. Check if feature is enabled
        val isNudgesEnabled = settingsRepository.getGoalNudgesEnabled().first()
        if (!isNudgesEnabled) {
            Log.d(logTag, "Goal nudges disabled, skipping surplus check")
            return Result.success()
        }

        // 2. Compute previous month
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        val prevYear = cal.get(Calendar.YEAR)
        val prevMonth = cal.get(Calendar.MONTH) + 1 // 1-indexed for budget storage

        // 3. Get budget for previous month
        val budget = settingsRepository.getOverallBudgetForMonth(prevYear, prevMonth).first() ?: 0f
        if (budget <= 0f) {
            Log.d(logTag, "No budget set for previous month, skipping surplus check")
            return Result.success()
        }

        // 4. Get expenses for previous month
        val db = AppDatabase.getInstance(applicationContext)

        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis

        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        val endTime = cal.timeInMillis

        val transactionAnalyticsDao = db.transactionAnalyticsDao()
        val expenses = transactionAnalyticsDao.getFinancialSummaryForRangeFlow(startTime, endTime).firstOrNull()?.totalExpenses ?: 0.0

        val surplus = budget.toDouble() - expenses
        Log.d(logTag, "Previous month budget: $budget, expenses: $expenses, surplus: $surplus")

        if (surplus > 0) {
            // 5. Get active goals
            val activeGoals = db.goalDao().getActiveGoals().firstOrNull() ?: emptyList<Goal>()
            if (activeGoals.isNotEmpty()) {
                // Find top priority goal
                val topGoal = db.goalDao().getAllGoalsWithAccountName().first().firstOrNull { it.id == activeGoals.first().id }

                Log.d(logTag, "Found surplus of $surplus and active goals. Sending notification.")
                NotificationHelper.showGoalSurplusNotification(applicationContext, surplus, topGoal)
            } else {
                Log.d(logTag, "Surplus exists but no active goals to allocate to")
            }
        } else {
            Log.d(logTag, "No surplus found")
        }

        return Result.success()
    }
}

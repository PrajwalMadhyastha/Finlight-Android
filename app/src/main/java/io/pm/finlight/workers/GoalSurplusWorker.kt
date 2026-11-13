// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/GoalSurplusWorker.kt
// REASON: FEATURE (Issue #104) - Smart Allocation Prompts
// A monthly worker that checks for budget surplus from the previous month
// and sends a nudge to allocate it to active savings goals.
//
// REFACTOR (Issue #392): Zero-DAO migration, Safe non-retry rescheduling expression,
// and safe surplus prompt idempotency tracking via IFeatureSettingsRepository/ISettingsRepository.
// =================================================================================
package io.pm.finlight.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import io.pm.finlight.Goal
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.Calendar

private const val TAG = "GoalSurplusWorker"

class GoalSurplusWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val dispatcherProvider = ServiceLocator.provideDispatcherProvider(applicationContext)
        return withContext(dispatcherProvider.io) {
            val settingsRepository = ServiceLocator.provideSettingsRepository(applicationContext)
            val featureSettingsRepository = ServiceLocator.provideFeatureSettingsRepository(applicationContext)
            val transactionRepository = ServiceLocator.provideTransactionRepository(applicationContext)
            val goalRepository = ServiceLocator.provideGoalRepository(applicationContext)
            val workScheduler = ServiceLocator.provideWorkScheduler(applicationContext)

            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
            val prevYear = cal.get(Calendar.YEAR)
            val prevMonth = cal.get(Calendar.MONTH) + 1 // 1-indexed for budget storage

            val isFinalAttempt = runAttemptCount >= 2
            val result =
                try {
                    val isNudgesEnabled = settingsRepository.getGoalNudgesEnabled().first()
                    val budget = settingsRepository.getOverallBudgetForMonth(prevYear, prevMonth).first() ?: 0f
                    if (!isNudgesEnabled || budget <= 0f) {
                        Log.d(TAG, "Surplus checks skipped (nudges=$isNudgesEnabled, budget=$budget)")
                        Result.success()
                    } else {
                        val lastPrompt = featureSettingsRepository.getLastSurplusPrompt().first()
                        if (lastPrompt != Pair(prevYear, prevMonth)) {
                            cal.set(Calendar.DAY_OF_MONTH, 1)
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            val startTime = cal.timeInMillis

                            cal.add(Calendar.MONTH, 1)
                            cal.add(Calendar.MILLISECOND, -1)
                            val endTime = cal.timeInMillis

                            val expenses =
                                transactionRepository.getFinancialSummaryForRangeFlow(startTime, endTime)
                                    .firstOrNull()?.totalExpenses ?: 0.0

                            val surplus = budget.toDouble() - expenses
                            Log.d(TAG, "Previous month budget: $budget, expenses: $expenses, surplus: $surplus")

                            if (surplus > 0) {
                                val activeGoals = goalRepository.getActiveGoals().firstOrNull() ?: emptyList<Goal>()
                                if (activeGoals.isNotEmpty()) {
                                    val topGoal =
                                        goalRepository.getAllGoalsWithAccountName().first()
                                            .firstOrNull { it.id == activeGoals.first().id }

                                    Log.d(TAG, "Found surplus of $surplus and active goals. Sending notification.")
                                    NotificationHelper.showGoalSurplusNotification(applicationContext, surplus, topGoal)
                                } else {
                                    Log.d(TAG, "Surplus exists but no active goals to allocate to")
                                }
                            } else {
                                Log.d(TAG, "No surplus found")
                            }

                            featureSettingsRepository.saveLastSurplusPrompt(prevYear, prevMonth)
                        }
                        Result.success()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "GoalSurplusWorker failed (attempt $runAttemptCount)", e)
                    if (isFinalAttempt) Result.failure() else Result.retry()
                }

            if (result != Result.retry()) {
                workScheduler.scheduleGoalSurplusWorker(
                    forceNextMonth = true,
                    policy = ExistingWorkPolicy.REPLACE,
                )
            }
            result
        }
    }
}

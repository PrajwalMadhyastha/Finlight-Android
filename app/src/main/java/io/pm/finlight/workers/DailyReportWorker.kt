// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/DailyReportWorker.kt
// REASON: FEATURE (Smart Notifications) - The worker's logic has been completely
// rewritten to be more intelligent. It now calculates a 7-day rolling average for
// spending and uses a series of contextual rules to generate a user-friendly,
// insightful notification title instead of a raw, potentially alarming percentage.
// FIX (Time-bound) - All Calendar instances now explicitly set SECOND and
// MILLISECOND to their boundaries (0 or 59/999). This prevents calculation
// errors caused by partial-day time ranges.
//
// REASON: BUG FIX (Report Mismatch) - The worker's date logic has been updated.
// Instead of a literal calendar "yesterday" (00:00 to 23:59), it now calculates
// a rolling "last 24 hours" window based on the current time. This perfectly
// matches the logic in `TimePeriodReportScreen` and ensures the number in the
// notification matches the number in the report it links to.
//
// REFACTOR (Issue #387): Route queries through ITransactionRepository, execute
// travel mode expiration check first, enforce non-retry rescheduling invariant
// without bare return inside try, cap retries at 3, rethrow CancellationException,
// and route rescheduling through IWorkScheduler.
// =================================================================================
package io.pm.finlight

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

class DailyReportWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val dispatcherProvider = ServiceLocator.provideDispatcherProvider(applicationContext)
        return withContext(dispatcherProvider.io) {
            if (runAttemptCount >= 3) return@withContext Result.failure()

            val workScheduler = ServiceLocator.provideWorkScheduler(applicationContext)
            val transactionRepository = ServiceLocator.provideTransactionRepository(applicationContext)
            val settingsRepository = ServiceLocator.provideSettingsRepository(applicationContext)
            val travelSettingsRepository = ServiceLocator.provideTravelSettingsRepository(applicationContext)

            val isFinalAttempt = runAttemptCount >= 2
            val result =
                try {
                    val now = Calendar.getInstance()

                    // Execute travel mode expiration check first
                    try {
                        val currentTravelSettings = travelSettingsRepository.getCurrentTravelModeSettings()
                        if (currentTravelSettings != null && now.timeInMillis > currentTravelSettings.endDate) {
                            travelSettingsRepository.saveTravelModeSettings(null)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("DailyReportWorker", "Failed to check or expire travel mode settings", e)
                    }

                    if (!settingsRepository.getDailyReportEnabled().first()) {
                        Log.d("DailyReportWorker", "Feature disabled. Skipping summary generation.")
                        Result.success()
                    } else {
                        // --- Define Date Ranges ---
                        // 1. "Last 24 Hours" period (rolling window matching TimePeriodReportScreen)
                        val reportEndTime = now.timeInMillis
                        val reportStartTime =
                            (now.clone() as Calendar).apply {
                                add(Calendar.HOUR_OF_DAY, -24)
                            }.timeInMillis

                        // 2. "Previous 7 Days" period (for calculating the average)
                        val sevenDaysAgoEnd =
                            (now.clone() as Calendar).apply {
                                add(Calendar.DAY_OF_YEAR, -2)
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                                set(Calendar.MILLISECOND, 999)
                            }.timeInMillis
                        val sevenDaysAgoStart =
                            (now.clone() as Calendar).apply {
                                add(Calendar.DAY_OF_YEAR, -8)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis

                        // --- Fetch Data ---
                        val reportSummary = transactionRepository.getFinancialSummaryForRange(reportStartTime, reportEndTime)
                        val yesterdayExpenses = reportSummary?.totalExpenses ?: 0.0

                        val weeklyAverage =
                            transactionRepository.getAverageDailySpendingForRange(sevenDaysAgoStart, sevenDaysAgoEnd) ?: 0.0

                        val topCategories = transactionRepository.getTopSpendingCategoriesForRange(reportStartTime, reportEndTime)

                        // --- Apply Contextual Logic ---
                        val title =
                            when {
                                // Scenario D: No Spending
                                yesterdayExpenses == 0.0 -> "No spending recorded yesterday. Keep it up!"

                                // Scenario C: Significant Drop
                                weeklyAverage > 0 && (yesterdayExpenses / weeklyAverage) < 0.20 ->
                                    "Great job! Spending was well below average yesterday."

                                // Scenario B: Normal Fluctuation
                                weeklyAverage > 0 && (yesterdayExpenses / weeklyAverage) in 0.90..1.10 ->
                                    "Your spending is on track with your weekly average."

                                // Scenario A: Major Spike
                                weeklyAverage > 0 && (yesterdayExpenses / weeklyAverage) > 1.50 ->
                                    "Heads up: Spending was higher than usual yesterday."

                                // Default Case (Catch-All)
                                else -> "Here's your daily summary."
                            }

                        NotificationHelper.showDailyReportNotification(
                            applicationContext,
                            title,
                            yesterdayExpenses,
                            topCategories,
                            now.timeInMillis,
                        )
                        Result.success()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("DailyReportWorker", "Execution failed on attempt $runAttemptCount", e)
                    if (isFinalAttempt) Result.failure() else Result.retry()
                }

            if (result != Result.retry()) {
                workScheduler.scheduleDailyReport(ExistingWorkPolicy.REPLACE)
            }
            result
        }
    }
}

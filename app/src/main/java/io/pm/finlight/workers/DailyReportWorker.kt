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
// =================================================================================
package io.pm.finlight

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.utils.NotificationHelper
import io.pm.finlight.utils.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class DailyReportWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val transactionDao = AppDatabase.getInstance(context).transactionDao()

                // --- Define Date Ranges ---
                val now = Calendar.getInstance()

                // 1. "Last 24 Hours" period (The Fix)
                // This now matches the logic in TimePeriodReportScreen.kt for TimePeriod.DAILY
                val reportEndTime = now.timeInMillis
                val reportStartTime = (now.clone() as Calendar).apply {
                    add(Calendar.HOUR_OF_DAY, -24)
                }.timeInMillis

                // 2. "Previous 7 Days" period (for calculating the average)
                val sevenDaysAgoEnd = (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -2)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59) // --- FIX
                    set(Calendar.MILLISECOND, 999) // --- FIX
                }.timeInMillis
                val sevenDaysAgoStart = (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -8)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0) // --- FIX
                    set(Calendar.MILLISECOND, 0) // --- FIX
                }.timeInMillis

                // --- Fetch Data ---
                // --- UPDATED: Use the new rolling 24-hour window ---
                val reportSummary = transactionDao.getFinancialSummaryForRange(reportStartTime, reportEndTime)
                val yesterdayExpenses = reportSummary?.totalExpenses ?: 0.0

                val weeklyAverage = transactionDao.getAverageDailySpendingForRange(sevenDaysAgoStart, sevenDaysAgoEnd) ?: 0.0

                // --- UPDATED: Use the new rolling 24-hour window ---
                val topCategories = transactionDao.getTopSpendingCategoriesForRange(reportStartTime, reportEndTime)

                // --- Apply Contextual Logic ---
                val title = when {
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

                NotificationHelper.showDailyReportNotification(context, title, yesterdayExpenses, topCategories, now.timeInMillis)

                ReminderManager.scheduleDailyReport(context)
                Result.success()
            } catch (e: Exception) {
                Log.e("DailyReportWorker", "Worker failed", e)
                Result.retry()
            }
        }
    }
}

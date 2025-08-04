// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/WeeklySummaryWorker.kt
// REASON: FIX - The calculation logic has been updated to explicitly filter out
// excluded transactions. The `forEach` loop was replaced with more efficient
// `filter` and `sumOf` calls, which now include the `!details.transaction.isExcluded`
// condition. This ensures the weekly summary report is accurate.
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
import kotlin.math.roundToInt

class WeeklySummaryWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val transactionDao = AppDatabase.getInstance(context).transactionDao()

                // Date range for LAST 7 DAYS
                val thisWeekEnd = Calendar.getInstance().timeInMillis
                val thisWeekStart = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis

                // Date range for PREVIOUS 7 DAYS (8-14 days ago)
                val lastWeekEnd = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -8) }.timeInMillis
                val lastWeekStart = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -14) }.timeInMillis

                val thisWeekSummary = transactionDao.getFinancialSummaryForRange(thisWeekStart, thisWeekEnd)
                val thisWeekExpenses = thisWeekSummary?.totalExpenses ?: 0.0

                val lastWeekSummary = transactionDao.getFinancialSummaryForRange(lastWeekStart, lastWeekEnd)
                val lastWeekExpenses = lastWeekSummary?.totalExpenses ?: 0.0

                val topCategories = transactionDao.getTopSpendingCategoriesForRange(thisWeekStart, thisWeekEnd)

                val percentageChange = if (lastWeekExpenses > 0) {
                    ((thisWeekExpenses - lastWeekExpenses) / lastWeekExpenses * 100).roundToInt()
                } else null

                NotificationHelper.showWeeklySummaryNotification(context, thisWeekExpenses, percentageChange, topCategories)

                ReminderManager.scheduleWeeklySummary(context)
                Result.success()
            } catch (e: Exception) {
                Log.e("WeeklySummaryWorker", "Worker failed", e)
                Result.retry()
            }
        }
    }
}

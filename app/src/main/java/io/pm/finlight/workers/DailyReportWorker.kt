// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/DailyReportWorker.kt
// REASON: BUG FIX - The worker's date calculation logic has been completely
// corrected. It now calculates a true rolling 24-hour window from the moment
// it executes. This ensures that the data gathered for the notification
// perfectly matches the data the user sees when they click the deep link,
// resolving the "no transactions" bug.
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

class DailyReportWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val transactionDao = AppDatabase.getInstance(context).transactionDao()

                // --- FIX: Calculate a true rolling 24-hour window from now ---
                val endDate = Calendar.getInstance().timeInMillis
                val startDate = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -24) }.timeInMillis

                // --- FIX: Calculate the comparison period as the 24 hours prior to the current period ---
                val previousPeriodEndDate = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -24) }.timeInMillis
                val previousPeriodStartDate = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -48) }.timeInMillis


                val currentPeriodSummary = transactionDao.getFinancialSummaryForRange(startDate, endDate)
                val currentPeriodExpenses = currentPeriodSummary?.totalExpenses ?: 0.0

                val previousPeriodSummary = transactionDao.getFinancialSummaryForRange(previousPeriodStartDate, previousPeriodEndDate)
                val previousPeriodExpenses = previousPeriodSummary?.totalExpenses ?: 0.0

                val topCategories = transactionDao.getTopSpendingCategoriesForRange(startDate, endDate)

                val percentageChange = if (previousPeriodExpenses > 0) {
                    ((currentPeriodExpenses - previousPeriodExpenses) / previousPeriodExpenses * 100).roundToInt()
                } else null

                // --- FIX: Pass the correct end date (now) to the notification helper ---
                NotificationHelper.showDailyReportNotification(context, currentPeriodExpenses, percentageChange, topCategories, endDate)

                ReminderManager.scheduleDailyReport(context)
                Result.success()
            } catch (e: Exception) {
                Log.e("DailyReportWorker", "Worker failed", e)
                Result.retry()
            }
        }
    }
}

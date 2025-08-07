// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/MonthlySummaryWorker.kt
// REASON: Added a call to ReminderManager.scheduleMonthlySummary at the end of the
// worker's execution to create a continuous chain of precisely scheduled tasks.
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

class MonthlySummaryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val transactionDao = AppDatabase.getInstance(context).transactionDao()

                // --- Date range for LAST MONTH ---
                val lastMonthCalendar = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                val lastMonthEnd = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
                val lastMonthStart = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis

                // --- Date range for PREVIOUS-TO-LAST MONTH ---
                val prevMonthEnd = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
                val prevMonthStart = Calendar.getInstance().apply { add(Calendar.MONTH, -2); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis


                val lastMonthSummary = transactionDao.getFinancialSummaryForRange(lastMonthStart, lastMonthEnd)
                val lastMonthExpenses = lastMonthSummary?.totalExpenses ?: 0.0

                val prevMonthSummary = transactionDao.getFinancialSummaryForRange(prevMonthStart, prevMonthEnd)
                val prevMonthExpenses = prevMonthSummary?.totalExpenses ?: 0.0

                val topCategories = transactionDao.getTopSpendingCategoriesForRange(lastMonthStart, lastMonthEnd)

                val percentageChange = if (prevMonthExpenses > 0) {
                    ((lastMonthExpenses - prevMonthExpenses) / prevMonthExpenses * 100).roundToInt()
                } else null

                NotificationHelper.showMonthlySummaryNotification(context, lastMonthCalendar, lastMonthExpenses, percentageChange, topCategories)

                ReminderManager.scheduleMonthlySummary(context)
                Result.success()
            } catch (e: Exception) {
                Log.e("MonthlySummaryWorker", "Worker failed", e)
                Result.retry()
            }
        }
    }
}

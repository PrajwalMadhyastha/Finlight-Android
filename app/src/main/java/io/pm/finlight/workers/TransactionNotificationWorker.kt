// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/TransactionNotificationWorker.kt
// REASON: FIX - The CoroutineWorker's constructor has been corrected to properly
// pass the application context. This resolves the "Argument type mismatch" and
// "No value passed for parameter" compilation errors.
// =================================================================================
package io.pm.finlight

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.Calendar

class TransactionNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) { // <-- FIX: Pass context to the parent constructor

    companion object {
        const val KEY_TRANSACTION_ID = "transaction_id"
    }

    override suspend fun doWork(): Result {
        val transactionId = inputData.getInt(KEY_TRANSACTION_ID, -1)
        if (transactionId == -1) {
            Log.e("TransactionNotificationWorker", "Invalid transactionId received.")
            return Result.failure()
        }

        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val transactionDao = db.transactionDao()

                // 1. Fetch full transaction details
                val details = transactionDao.getTransactionDetailsById(transactionId).firstOrNull()
                if (details == null) {
                    Log.e("TransactionNotificationWorker", "TransactionDetails not found for id: $transactionId")
                    return@withContext Result.failure()
                }

                // 2. Calculate monthly totals
                val calendar = Calendar.getInstance().apply { timeInMillis = details.transaction.date }
                val monthStart = (calendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
                val monthEnd = (calendar.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
                val summary = transactionDao.getFinancialSummaryForRange(monthStart, monthEnd)
                val monthlyTotal = if (details.transaction.transactionType == "income") summary?.totalIncome else summary?.totalExpenses

                // 3. Get visit count
                val visitCount = transactionDao.getTransactionCountForMerchantSuspend(details.transaction.originalDescription ?: details.transaction.description)

                // 4. Show the rich notification
                NotificationHelper.showRichTransactionNotification(
                    context = context,
                    details = details,
                    monthlyTotal = monthlyTotal ?: 0.0,
                    visitCount = visitCount
                )

                Result.success()
            } catch (e: Exception) {
                Log.e("TransactionNotificationWorker", "Worker failed for transaction id: $transactionId", e)
                Result.retry()
            }
        }
    }
}

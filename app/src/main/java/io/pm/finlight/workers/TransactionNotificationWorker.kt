// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/TransactionNotificationWorker.kt
// REASON: FIX - The CoroutineWorker's constructor has been corrected to properly
// pass the application context. This resolves the "Argument type mismatch" and
// "No value passed for parameter" compilation errors.
//
// REFACTOR (Issue #386): Migrated all DAO/AppDatabase queries to ITransactionRepository
// via ServiceLocator (Zero-DAO). Added retry check cap (runAttemptCount >= 3 and
// runAttemptCount >= 2 on error), and explicit rethrow of CancellationException.
// =================================================================================
package io.pm.finlight

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.Calendar

class TransactionNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    companion object {
        const val KEY_TRANSACTION_ID = "transaction_id"
    }

    override suspend fun doWork(): Result {
        if (runAttemptCount >= 3) {
            Log.e("TransactionNotificationWorker", "Max retries exceeded ($runAttemptCount). Failing permanently.")
            return Result.failure()
        }

        val transactionId = inputData.getInt(KEY_TRANSACTION_ID, -1)
        if (transactionId == -1) {
            Log.e("TransactionNotificationWorker", "Worker invoked without a valid transaction ID.")
            return Result.failure()
        }

        val dispatcherProvider = ServiceLocator.provideDispatcherProvider(context)
        return withContext(dispatcherProvider.io) {
            try {
                val transactionRepository = ServiceLocator.provideTransactionRepository(context)

                // 1. Fetch details
                val details = transactionRepository.getTransactionDetailsById(transactionId).firstOrNull()
                if (details == null) {
                    Log.e("TransactionNotificationWorker", "Transaction details not found for id: $transactionId")
                    return@withContext Result.failure()
                }

                // 2. Fetch monthly totals
                val calendar = Calendar.getInstance().apply { timeInMillis = details.transaction.date }
                val monthStart =
                    (calendar.clone() as Calendar).apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                    }.timeInMillis
                val monthEnd =
                    (calendar.clone() as Calendar).apply {
                        add(Calendar.MONTH, 1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        add(Calendar.DAY_OF_MONTH, -1)
                    }.timeInMillis
                val summary = transactionRepository.getFinancialSummaryForRange(monthStart, monthEnd)
                val monthlyTotal = if (details.transaction.transactionType == TransactionType.INCOME) summary?.totalIncome else summary?.totalExpenses

                // 3. Get visit count and merchant total spent
                val (visitCount, merchantTotalSpent) =
                    if (details.transaction.transactionType != TransactionType.INCOME) {
                        val count =
                            transactionRepository.getTransactionCountForMerchant(
                                details.transaction.description,
                            ).first()
                        val totalSpent =
                            transactionRepository.getTotalSpentForMerchant(
                                details.transaction.description,
                            ).first()
                        Pair(count, totalSpent)
                    } else {
                        Pair(0, 0.0)
                    }

                // 4. Show the rich notification
                NotificationHelper.showRichTransactionNotification(
                    context = context,
                    details = details,
                    monthlyTotal = monthlyTotal ?: 0.0,
                    visitCount = visitCount,
                    merchantTotalSpent = merchantTotalSpent,
                )

                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TransactionNotificationWorker", "Worker failed for transaction id: $transactionId", e)
                if (runAttemptCount >= 2) Result.failure() else Result.retry()
            }
        }
    }
}

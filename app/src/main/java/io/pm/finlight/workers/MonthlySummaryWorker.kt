// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/MonthlySummaryWorker.kt
// REASON: Added a call to ReminderManager.scheduleMonthlySummary at the end of the
// worker's execution to create a continuous chain of precisely scheduled tasks.
// FIX (Time-bound) - All Calendar instances now explicitly set SECOND and
// MILLISECOND to their boundaries (0 or 59/999). This prevents calculation
// errors caused by partial-day time ranges.
//
// REFACTOR (Issue #387): Route queries through ITransactionRepository, cap retries
// at 3, eliminate bare returns, rethrow CancellationException, and apply uniform
// non-retry rescheduling (result != Result.retry()) via IWorkScheduler.
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
import kotlin.math.roundToInt

class MonthlySummaryWorker(
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

            val isFinalAttempt = runAttemptCount >= 2
            val result =
                try {
                    if (!settingsRepository.getMonthlySummaryEnabled().first()) {
                        Log.d("MonthlySummaryWorker", "Feature disabled. Skipping summary generation.")
                        Result.success()
                    } else {
                        // --- Date range for LAST MONTH ---
                        val lastMonthCalendar = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                        val lastMonthEnd =
                            Calendar.getInstance().apply {
                                set(Calendar.DAY_OF_MONTH, 1)
                                add(Calendar.DAY_OF_MONTH, -1)
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                                set(Calendar.MILLISECOND, 999)
                            }.timeInMillis
                        val lastMonthStart =
                            Calendar.getInstance().apply {
                                add(Calendar.MONTH, -1)
                                set(Calendar.DAY_OF_MONTH, 1)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis

                        // --- Date range for PREVIOUS-TO-LAST MONTH ---
                        val prevMonthEnd =
                            Calendar.getInstance().apply {
                                add(Calendar.MONTH, -1)
                                set(Calendar.DAY_OF_MONTH, 1)
                                add(Calendar.DAY_OF_MONTH, -1)
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                                set(Calendar.MILLISECOND, 999)
                            }.timeInMillis
                        val prevMonthStart =
                            Calendar.getInstance().apply {
                                add(Calendar.MONTH, -2)
                                set(Calendar.DAY_OF_MONTH, 1)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis

                        val lastMonthSummary = transactionRepository.getFinancialSummaryForRange(lastMonthStart, lastMonthEnd)
                        val lastMonthExpenses = lastMonthSummary?.totalExpenses ?: 0.0

                        val prevMonthSummary = transactionRepository.getFinancialSummaryForRange(prevMonthStart, prevMonthEnd)
                        val prevMonthExpenses = prevMonthSummary?.totalExpenses ?: 0.0

                        val topCategories = transactionRepository.getTopSpendingCategoriesForRange(lastMonthStart, lastMonthEnd)

                        val percentageChange =
                            if (prevMonthExpenses > 0) {
                                ((lastMonthExpenses - prevMonthExpenses) / prevMonthExpenses * 100).roundToInt()
                            } else {
                                null
                            }

                        NotificationHelper.showMonthlySummaryNotification(
                            applicationContext,
                            lastMonthCalendar,
                            lastMonthExpenses,
                            percentageChange,
                            topCategories,
                        )
                        Result.success()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MonthlySummaryWorker", "Execution failed on attempt $runAttemptCount", e)
                    if (isFinalAttempt) Result.failure() else Result.retry()
                }

            if (result != Result.retry()) {
                workScheduler.scheduleMonthlySummary(ExistingWorkPolicy.REPLACE)
            }
            result
        }
    }
}

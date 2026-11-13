// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/WeeklySummaryWorker.kt
// REASON: FIX - The calculation logic has been updated to explicitly filter out
// excluded transactions. The `forEach` loop was replaced with more efficient
// `filter` and `sumOf` calls, which now include the `!details.transaction.isExcluded`
// condition. This ensures the weekly summary report is accurate.
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

private const val TAG = "WeeklySummaryWorker"

class WeeklySummaryWorker(
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
                    if (!settingsRepository.getWeeklySummaryEnabled().first()) {
                        Log.d(TAG, "Feature disabled. Skipping summary generation.")
                        Result.success()
                    } else {
                        // Date range for LAST 7 DAYS
                        val thisWeekEnd =
                            Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                                set(Calendar.MILLISECOND, 999)
                            }.timeInMillis
                        val thisWeekStart =
                            Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, -7)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis

                        // Date range for PREVIOUS 7 DAYS (8-14 days ago)
                        val lastWeekEnd =
                            Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, -8)
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                                set(Calendar.MILLISECOND, 999)
                            }.timeInMillis
                        val lastWeekStart =
                            Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, -14)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis

                        val thisWeekSummary = transactionRepository.getFinancialSummaryForRange(thisWeekStart, thisWeekEnd)
                        val thisWeekExpenses = thisWeekSummary?.totalExpenses ?: 0.0

                        val lastWeekSummary = transactionRepository.getFinancialSummaryForRange(lastWeekStart, lastWeekEnd)
                        val lastWeekExpenses = lastWeekSummary?.totalExpenses ?: 0.0

                        val topCategories = transactionRepository.getTopSpendingCategoriesForRange(thisWeekStart, thisWeekEnd)

                        val percentageChange =
                            if (lastWeekExpenses > 0) {
                                ((thisWeekExpenses - lastWeekExpenses) / lastWeekExpenses * 100).roundToInt()
                            } else {
                                null
                            }

                        NotificationHelper.showWeeklySummaryNotification(
                            applicationContext,
                            thisWeekExpenses,
                            percentageChange,
                            topCategories,
                        )
                        Result.success()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Execution failed on attempt $runAttemptCount", e)
                    if (isFinalAttempt) Result.failure() else Result.retry()
                }

            if (result != Result.retry()) {
                workScheduler.scheduleWeeklySummary(ExistingWorkPolicy.REPLACE)
            }
            result
        }
    }
}

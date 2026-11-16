// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/RecurringTransactionWorker.kt
// REASON: REFACTOR (Issue #390) - Eliminates business logic and DAO calls from
// RecurringTransactionWorker, delegating execution to ProcessDueRecurringTransactionsUseCase.
// Presentation effects (notifications) are dispatched on Main. Evaluates without
// bare returns inside try, and reschedules via IWorkScheduler on non-retry termination
// (result != Result.retry()), preserving exponential retry backoff.
// =================================================================================
package io.pm.finlight

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.ProcessedRecurringResult
import io.pm.finlight.utils.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "RecurringTxWorker"

class RecurringTransactionWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val dispatcherProvider = ServiceLocator.provideDispatcherProvider(applicationContext)
        return withContext(dispatcherProvider.io) {
            val workScheduler = ServiceLocator.provideWorkScheduler(applicationContext)
            val isFinalAttempt = runAttemptCount >= 2
            val result =
                try {
                    val settingsRepository = ServiceLocator.provideSettingsRepository(applicationContext)
                    val isEnabled = settingsRepository.getRecurringTransactionsEnabled().first()
                    if (!isEnabled) {
                        Log.d(TAG, "Feature disabled. Halting recurring work chain.")
                        Result.success()
                    } else {
                        val useCase =
                            ServiceLocator.provideProcessDueRecurringTransactionsUseCase(applicationContext)
                        val results = useCase()
                        withContext(dispatcherProvider.main) {
                            dispatchPresentationEffects(results)
                        }
                        Result.success()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Execution failed on attempt $runAttemptCount", e)
                    if (isFinalAttempt) Result.failure() else Result.retry()
                }

            if (result != Result.retry()) {
                workScheduler.scheduleRecurringTransactionWorker(
                    policy = ExistingWorkPolicy.REPLACE,
                )
            }
            result
        }
    }

    private fun dispatchPresentationEffects(results: List<ProcessedRecurringResult>) {
        results.forEach { result ->
            when (result) {
                is ProcessedRecurringResult.AutoApproved -> {
                    NotificationHelper.showAutoApprovedPaymentNotification(applicationContext, result.rule)
                }
                is ProcessedRecurringResult.PendingDraftCreated -> {
                    NotificationHelper.showRecurringTransactionDueNotification(
                        applicationContext,
                        result.rule,
                        result.draftTransactionId.toInt(),
                    )
                }
                is ProcessedRecurringResult.Skipped -> {
                    // Skipped rules do not trigger presentation effects
                }
            }
        }
    }
}

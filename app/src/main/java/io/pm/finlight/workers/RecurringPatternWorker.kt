// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/RecurringPatternWorker.kt
// REASON: REFACTOR (Issue #391) - Eliminates eager DAO initializers, delegates pattern
// analysis to AnalyzeRecurringPatternsUseCase, guards notifications against duplicate
// spam via DataStore cache, suppresses active rules, evaluates try without bare returns,
// and reschedules via IWorkScheduler on non-retry termination preserving exponential backoff.
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

private const val TAG = "RecurringPatternWorker"

class RecurringPatternWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val dispatcherProvider = ServiceLocator.provideDispatcherProvider(applicationContext)
        return withContext(dispatcherProvider.io) {
            val workScheduler = ServiceLocator.provideWorkScheduler(applicationContext)
            val settingsRepository = ServiceLocator.provideSettingsRepository(applicationContext)
            val recurringTransactionRepository =
                ServiceLocator.provideRecurringTransactionRepository(applicationContext)
            val featureSettingsRepository =
                ServiceLocator.provideFeatureSettingsRepository(applicationContext)

            val isFinalAttempt = runAttemptCount >= 2
            val result =
                try {
                    val isEnabled = settingsRepository.getRecurringTransactionsEnabled().first()
                    if (!isEnabled) {
                        Log.d(TAG, "Feature disabled. Halting pattern work chain.")
                        Result.success()
                    } else {
                        val useCase =
                            ServiceLocator.provideAnalyzeRecurringPatternsUseCase(applicationContext)
                        val currentTimeMillis = System.currentTimeMillis()
                        val detectedPatterns = useCase(currentTimeMillis)
                        val activeRules = recurringTransactionRepository.getAllRulesList()
                        val notifiedSignatures = featureSettingsRepository.getNotifiedPatternSignatures()

                        for (pattern in detectedPatterns) {
                            if (pattern.isDismissed) continue
                            val hasActiveRule =
                                activeRules.any { rule ->
                                    rule.description.equals(pattern.description, ignoreCase = true) &&
                                        rule.accountId == pattern.accountId
                                }
                            if (hasActiveRule) continue

                            if (pattern.smsSignature !in notifiedSignatures) {
                                NotificationHelper.showRecurringPatternDetectedNotification(
                                    applicationContext,
                                    pattern,
                                )
                                featureSettingsRepository.markPatternNotified(pattern.smsSignature)
                            }
                        }
                        Result.success()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Pattern analysis failed on attempt $runAttemptCount", e)
                    if (isFinalAttempt) Result.failure() else Result.retry()
                }

            if (result != Result.retry()) {
                workScheduler.scheduleRecurringPatternWorker(ExistingWorkPolicy.REPLACE)
            }
            result
        }
    }
}

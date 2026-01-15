// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/RecurringTransactionWorker.kt
// REASON: FIX - Added check for `recurring_transactions_enabled` preference.
// If disabled, the worker reschedules itself and returns success immediately,
// preventing notifications and processing. This ensures the feature is completely
// disabled at the platform level.
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

class RecurringTransactionWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                // FIX: Check if the feature is enabled before proceeding
                val settingsRepo = SettingsRepository(context)
                val isEnabled = settingsRepo.getRecurringTransactionsEnabled().first()

                if (!isEnabled) {
                    Log.d("RecurringTransactionWorker", "Feature disabled. Rescheduling and exiting.")
                    // Important: We must still reschedule the worker, otherwise it will stop running forever.
                    // If the user re-enables the feature later, we want the worker to be alive to catch it.
                    ReminderManager.scheduleRecurringTransactionWorker(context)
                    return@withContext Result.success()
                }

                val db = AppDatabase.getInstance(context)
                val recurringDao = db.recurringTransactionDao()

                val allRules = recurringDao.getAllRulesList()

                allRules.forEach { rule ->
                    if (isDue(rule)) {
                        val potentialTxn = PotentialTransaction(
                            sourceSmsId = rule.id.toLong(),
                            smsSender = "Recurring Rule",
                            amount = rule.amount,
                            transactionType = rule.transactionType,
                            merchantName = rule.description,
                            originalMessage = "Recurring payment for ${rule.description}",
                            sourceSmsHash = "recurring_${rule.id}",
                            date = System.currentTimeMillis() // Use current time for due transactions
                        )
                        NotificationHelper.showRecurringTransactionDueNotification(context, potentialTxn)
                    }
                }

                ReminderManager.scheduleRecurringTransactionWorker(context)
                Result.success()
            } catch (e: Exception) {
                Log.e("RecurringTxnWorker", "Worker failed", e)
                Result.retry()
            }
        }
    }

    private fun isDue(rule: RecurringTransaction): Boolean {
        val today = Calendar.getInstance()
        val ruleStartCal = Calendar.getInstance().apply { timeInMillis = rule.startDate }

        if (today.before(ruleStartCal)) {
            return false
        }

        if (rule.lastRunDate == null) {
            return true
        }

        val lastRunCal = Calendar.getInstance().apply { timeInMillis = rule.lastRunDate }
        val nextDueDate = (lastRunCal.clone() as Calendar).apply {
            when (rule.recurrenceInterval) {
                "Daily" -> add(Calendar.DAY_OF_YEAR, 1)
                "Weekly" -> add(Calendar.WEEK_OF_YEAR, 1)
                "Monthly" -> add(Calendar.MONTH, 1)
                "Yearly" -> add(Calendar.YEAR, 1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayStartOfDay = (today.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return !todayStartOfDay.before(nextDueDate)
    }
}

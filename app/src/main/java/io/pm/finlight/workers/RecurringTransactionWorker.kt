// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/RecurringTransactionWorker.kt
// REASON: REFACTOR - The worker's core logic has been completely overhauled.
// Instead of creating a new transaction, it now calls a new function in
// NotificationHelper to show a notification for each due rule. This aligns
// with the new user-driven workflow. The isDue function is updated to be more
// robust, checking for missed runs.
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

class RecurringTransactionWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val recurringDao = db.recurringTransactionDao()

                val allRules = recurringDao.getAllRulesList()

                allRules.forEach { rule ->
                    if (isDue(rule)) {
                        // --- NEW: Instead of creating a transaction, show a notification ---
                        val potentialTxn = PotentialTransaction(
                            sourceSmsId = rule.id.toLong(), // Re-using this field for the rule ID
                            smsSender = "Recurring Rule",
                            amount = rule.amount,
                            transactionType = rule.transactionType,
                            merchantName = rule.description,
                            originalMessage = "Recurring payment for ${rule.description}",
                            sourceSmsHash = "recurring_${rule.id}"
                        )
                        NotificationHelper.showRecurringTransactionDueNotification(context, potentialTxn)
                    }
                }

                // Reschedule for the next day
                ReminderManager.scheduleRecurringTransactionWorker(context)
                Result.success()
            } catch (e: Exception) {
                Log.e("RecurringTxnWorker", "Worker failed", e)
                Result.retry()
            }
        }
    }

    /**
     * Determines if a recurring transaction rule is due to be executed today.
     */
    private fun isDue(rule: RecurringTransaction): Boolean {
        val today = Calendar.getInstance()
        val ruleStartCal = Calendar.getInstance().apply { timeInMillis = rule.startDate }

        // If the rule's start date is in the future, it's not due yet.
        if (today.before(ruleStartCal)) {
            return false
        }

        // If the rule has never run, it's due.
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
            // Set to beginning of the day for consistent comparison
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

        // Check if today is on or after the calculated next due date.
        // We use 'on or after' to catch up on any missed runs (e.g., if the device was off).
        return !todayStartOfDay.before(nextDueDate)
    }
}

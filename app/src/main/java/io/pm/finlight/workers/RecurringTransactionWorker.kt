// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/RecurringTransactionWorker.kt
// REASON: FIX - The PotentialTransaction object created by the worker is now
// correctly initialized with the current system timestamp. This ensures it is
// compatible with the updated data class structure and prevents potential
// date-related bugs in the linking/approval screens.
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

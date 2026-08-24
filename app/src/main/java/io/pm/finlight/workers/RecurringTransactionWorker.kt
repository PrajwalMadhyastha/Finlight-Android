// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/workers/RecurringTransactionWorker.kt
// REASON: FEATURE (Issue #105) - Replaced the notification-only approach with a
// draft transaction model. When a rule is due, the worker now creates a PENDING
// Transaction in the database instead of just firing a notification. This "draft"
// is the single source of truth for what needs user action.
//
// Two execution paths:
// 1. autoApprove = true → Saves the transaction directly as CONFIRMED, updates
//    lastRunDate, and sends a quiet "Auto-paid" notification.
// 2. autoApprove = false (default) → Saves as PENDING, fires the existing
//    "due" notification (deep link updated to ConfirmPending sheet).
//
// Guards:
// - Skips expired rules (endDate is in the past).
// - Skips rules where a PENDING draft already exists (idempotent run).
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
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    private val tag = "RecurringTxnWorker"

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val settingsRepo = SettingsRepository(context)
                val isEnabled = settingsRepo.getRecurringTransactionsEnabled().first()

                if (!isEnabled) {
                    Log.d(tag, "Feature disabled. Rescheduling and exiting.")
                    ReminderManager.scheduleRecurringTransactionWorker(context)
                    return@withContext Result.success()
                }

                val db = AppDatabase.getInstance(context)
                val recurringDao = db.recurringTransactionDao()
                val transactionQueryDao = db.transactionQueryDao()
                val transactionWriteDao = db.transactionWriteDao()

                val allRules = recurringDao.getAllRulesList()
                val now = System.currentTimeMillis()

                allRules.forEach { rule ->
                    // --- Guard 1: Skip expired rules ---
                    if (rule.endDate != null && now > rule.endDate) {
                        Log.d(tag, "Rule '${rule.description}' has passed its end date. Skipping.")
                        return@forEach
                    }

                    if (isDue(rule)) {
                        // --- Guard 2: Idempotency — skip if a PENDING draft already exists ---
                        val existingDraft = transactionQueryDao.getPendingTransactionForRule(rule.id)
                        if (existingDraft != null) {
                            Log.d(tag, "Rule '${rule.description}' already has a pending draft (id=${existingDraft.id}). Skipping.")
                            return@forEach
                        }

                        if (rule.autoApprove) {
                            // --- Auto-Approve Path: Confirm immediately ---
                            val confirmedTxn =
                                Transaction(
                                    description = rule.description,
                                    amount = rule.amount,
                                    transactionType = TransactionType.fromString(rule.transactionType),
                                    date = now,
                                    accountId = rule.accountId,
                                    categoryId = rule.categoryId,
                                    notes = "Auto-approved by recurring rule",
                                    source = "Recurring Rule",
                                    status = TransactionStatus.CONFIRMED,
                                    recurringRuleId = rule.id,
                                )
                            transactionWriteDao.insert(confirmedTxn)
                            recurringDao.updateLastRunDate(rule.id, now)
                            Log.i(tag, "Auto-approved recurring payment: '${rule.description}'")
                            NotificationHelper.showAutoApprovedPaymentNotification(context, rule)
                        } else {
                            // --- Standard Path: Create a PENDING draft ---
                            val draftTxn =
                                Transaction(
                                    description = rule.description,
                                    amount = rule.amount,
                                    transactionType = TransactionType.fromString(rule.transactionType),
                                    date = now,
                                    accountId = rule.accountId,
                                    categoryId = rule.categoryId,
                                    notes = "",
                                    source = "Recurring Rule (Pending)",
                                    status = TransactionStatus.PENDING,
                                    recurringRuleId = rule.id,
                                )
                            val newDraftId = transactionWriteDao.insert(draftTxn)
                            Log.i(tag, "Created PENDING draft (id=$newDraftId) for rule '${rule.description}'")
                            NotificationHelper.showRecurringTransactionDueNotification(context, rule, newDraftId.toInt())
                        }
                    }
                }

                ReminderManager.scheduleRecurringTransactionWorker(context)
                Result.success()
            } catch (e: Exception) {
                Log.e(tag, "Worker failed", e)
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
        val nextDueDate =
            (lastRunCal.clone() as Calendar).apply {
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

        val todayStartOfDay =
            (today.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        return !todayStartOfDay.before(nextDueDate)
    }
}

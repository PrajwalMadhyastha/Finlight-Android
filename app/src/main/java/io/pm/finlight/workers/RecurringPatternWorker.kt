// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/RecurringPatternWorker.kt
// REASON: FIX - The call to `recurringTransactionDao.insert()` now correctly
// returns a Long, so the `.toInt()` conversion works as expected, resolving
// the compilation error.
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
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class RecurringPatternWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val transactionDao = AppDatabase.getInstance(context).transactionDao()
    private val patternDao = AppDatabase.getInstance(context).recurringPatternDao()
    private val recurringTransactionDao = AppDatabase.getInstance(context).recurringTransactionDao()

    companion object {
        // The time window to look back for transactions to analyze. 90 days is a good balance.
        private val ANALYSIS_WINDOW_DAYS = 90L
        // The minimum number of times a transaction must occur to be considered a pattern.
        private const val MIN_OCCURRENCES_FOR_PATTERN = 3
        // The tolerance for interval matching (in days).
        private const val WEEKLY_TOLERANCE_DAYS = 1
        private const val MONTHLY_TOLERANCE_DAYS = 3
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Fetch recent transactions that have an SMS signature.
                val recentTransactions = transactionDao.getTransactionsWithSignatureSince(
                    System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ANALYSIS_WINDOW_DAYS)
                )

                // 2. Update the pattern database with these transactions.
                for (transaction in recentTransactions) {
                    val signature = transaction.smsSignature ?: continue
                    val existingPattern = patternDao.getPatternBySignature(signature)

                    if (existingPattern == null) {
                        // First time seeing this signature, create a new pattern entry.
                        patternDao.insert(
                            RecurringPattern(
                                smsSignature = signature,
                                description = transaction.description,
                                amount = transaction.amount,
                                transactionType = transaction.transactionType,
                                accountId = transaction.accountId,
                                categoryId = transaction.categoryId,
                                occurrences = 1,
                                firstSeen = transaction.date,
                                lastSeen = transaction.date
                            )
                        )
                    } else {
                        // We've seen this before, update the existing pattern.
                        existingPattern.occurrences += 1
                        existingPattern.lastSeen = transaction.date
                        patternDao.update(existingPattern)
                    }
                }

                // 3. Analyze all patterns that have occurred enough times.
                val allPatterns = patternDao.getAllPatterns()
                for (pattern in allPatterns) {
                    if (pattern.occurrences >= MIN_OCCURRENCES_FOR_PATTERN) {
                        analyzeAndCreateRuleIfNeeded(pattern)
                    }
                }

                // 4. Reschedule the worker for the next run.
                ReminderManager.scheduleRecurringPatternWorker(context)
                Result.success()
            } catch (e: Exception) {
                Log.e("RecurringPatternWorker", "Worker failed", e)
                Result.retry()
            }
        }
    }

    private suspend fun analyzeAndCreateRuleIfNeeded(pattern: RecurringPattern) {
        val transactions = transactionDao.getTransactionsBySignature(pattern.smsSignature)
        if (transactions.size < MIN_OCCURRENCES_FOR_PATTERN) return

        val timestamps = transactions.map { it.date }.sorted()
        val intervals = timestamps.zipWithNext { a, b -> TimeUnit.MILLISECONDS.toDays(b - a) }

        // Check for weekly pattern
        val isWeekly = intervals.all { abs(it - 7) <= WEEKLY_TOLERANCE_DAYS }
        if (isWeekly) {
            createRuleAndNotify(pattern, "Weekly")
            return
        }

        // Check for monthly pattern
        val isMonthly = intervals.all { abs(it - 30) <= MONTHLY_TOLERANCE_DAYS }
        if (isMonthly) {
            createRuleAndNotify(pattern, "Monthly")
            return
        }
    }

    private suspend fun createRuleAndNotify(pattern: RecurringPattern, interval: String) {
        // Create the recurring transaction rule
        val newRule = RecurringTransaction(
            description = pattern.description,
            amount = pattern.amount,
            transactionType = pattern.transactionType,
            recurrenceInterval = interval,
            startDate = pattern.lastSeen,
            accountId = pattern.accountId,
            categoryId = pattern.categoryId,
            lastRunDate = pattern.lastSeen
        )
        val newRuleId = recurringTransactionDao.insert(newRule).toInt()

        // Notify the user
        NotificationHelper.showRecurringPatternDetectedNotification(context, newRule.copy(id = newRuleId))

        // Clean up the pattern from the analysis table to prevent re-detection
        patternDao.deleteBySignature(pattern.smsSignature)
    }
}

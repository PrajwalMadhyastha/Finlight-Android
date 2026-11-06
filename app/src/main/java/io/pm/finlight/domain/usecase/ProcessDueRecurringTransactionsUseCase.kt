package io.pm.finlight.domain.usecase

import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionStatus
import io.pm.finlight.data.TransactionRunner
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Result representing the execution outcome of a recurring transaction rule.
 */
sealed class ProcessedRecurringResult {
    data class AutoApproved(val rule: RecurringTransaction, val transactionId: Long) : ProcessedRecurringResult()

    data class PendingDraftCreated(val rule: RecurringTransaction, val draftTransactionId: Long) : ProcessedRecurringResult()

    data class Skipped(val rule: RecurringTransaction, val reason: String) : ProcessedRecurringResult()
}

/**
 * UseCase to atomically evaluate and execute all due recurring transactions.
 */
interface ProcessDueRecurringTransactionsUseCase {
    suspend operator fun invoke(
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): List<ProcessedRecurringResult>
}

/**
 * Default implementation of [ProcessDueRecurringTransactionsUseCase] guaranteeing per-rule transactional atomicity,
 * schedule normalization, safe calendar month clamping, and idempotency guards.
 */
class DefaultProcessDueRecurringTransactionsUseCase(
    private val recurringTransactionRepository: IRecurringTransactionRepository,
    private val transactionRepository: ITransactionRepository,
    private val transactionRunner: TransactionRunner,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ProcessDueRecurringTransactionsUseCase {
    override suspend operator fun invoke(
        currentTimeMillis: Long,
    ): List<ProcessedRecurringResult> =
        withContext(dispatcherProvider.io) {
            val allRules = recurringTransactionRepository.getAllRulesList()
            val results = mutableListOf<ProcessedRecurringResult>()

            val todayStart =
                Calendar.getInstance().apply {
                    timeInMillis = currentTimeMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

            allRules.forEach { rule ->
                val ruleStart =
                    Calendar.getInstance().apply {
                        timeInMillis = rule.startDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                if (todayStart < ruleStart) return@forEach
                if (rule.endDate != null && currentTimeMillis > rule.endDate) return@forEach
                if (!isDue(rule, currentTimeMillis)) return@forEach

                val cycleStartEpochMs = calculateTargetCycleStartEpochMs(rule, currentTimeMillis)

                val result =
                    transactionRunner.run {
                        if (transactionRepository.hasExecutedTransactionForRuleSince(rule.id, cycleStartEpochMs)) {
                            ProcessedRecurringResult.Skipped(rule, "Already executed transaction for current cycle")
                        } else {
                            val existingDraft = transactionRepository.getPendingTransactionForRule(rule.id)
                            if (existingDraft != null) {
                                ProcessedRecurringResult.Skipped(
                                    rule,
                                    "Rule '${rule.description}' already has a pending draft (id=${existingDraft.id})",
                                )
                            } else if (rule.autoApprove) {
                                val confirmedTxn =
                                    Transaction(
                                        description = rule.description,
                                        amount = rule.amount,
                                        transactionType = rule.transactionType,
                                        date = cycleStartEpochMs,
                                        accountId = rule.accountId,
                                        categoryId = rule.categoryId,
                                        notes = "Auto-approved by recurring rule",
                                        source = "Recurring Rule",
                                        status = TransactionStatus.CONFIRMED,
                                        recurringRuleId = rule.id,
                                    )
                                val transactionId = transactionRepository.insertTransaction(confirmedTxn)
                                recurringTransactionRepository.updateLastRunDate(rule.id, cycleStartEpochMs)
                                ProcessedRecurringResult.AutoApproved(rule, transactionId)
                            } else {
                                val draftTxn =
                                    Transaction(
                                        description = rule.description,
                                        amount = rule.amount,
                                        transactionType = rule.transactionType,
                                        date = cycleStartEpochMs,
                                        accountId = rule.accountId,
                                        categoryId = rule.categoryId,
                                        notes = "",
                                        source = "Recurring Rule (Pending)",
                                        status = TransactionStatus.PENDING,
                                        recurringRuleId = rule.id,
                                    )
                                val draftId = transactionRepository.insertTransaction(draftTxn)
                                ProcessedRecurringResult.PendingDraftCreated(rule, draftId)
                            }
                        }
                    }
                results.add(result)
            }

            results
        }
}

/**
 * Calculates the canonical cycle anchor epoch timestamp in milliseconds for a recurring transaction rule.
 *
 * Implements safe calendar month clamping, resetting DAY_OF_MONTH to 1 before month adjustments to neutralize
 * lenient 31-day rollovers and leap-year February 29 handling.
 */
internal fun calculateTargetCycleStartEpochMs(
    rule: RecurringTransaction,
    nowMs: Long,
): Long {
    val cal =
        Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            when (rule.recurrenceInterval) {
                "Daily" -> { /* Start of current day */ }
                "Weekly" -> {
                    val ruleCal = Calendar.getInstance().apply { timeInMillis = rule.startDate }
                    val targetDayOfWeek = ruleCal.get(Calendar.DAY_OF_WEEK)
                    while (get(Calendar.DAY_OF_WEEK) != targetDayOfWeek) {
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                }
                "Monthly" -> {
                    val ruleCal = Calendar.getInstance().apply { timeInMillis = rule.startDate }
                    val targetDayOfMonth = ruleCal.get(Calendar.DAY_OF_MONTH)
                    set(Calendar.DAY_OF_MONTH, 1) // Neutralize 31-day overflow
                    val maxDayInTargetMonth = getActualMaximum(Calendar.DAY_OF_MONTH)
                    set(Calendar.DAY_OF_MONTH, minOf(targetDayOfMonth, maxDayInTargetMonth))
                    if (timeInMillis > nowMs) {
                        set(Calendar.DAY_OF_MONTH, 1) // Neutralize overflow before decrement
                        add(Calendar.MONTH, -1)
                        val prevMax = getActualMaximum(Calendar.DAY_OF_MONTH)
                        set(Calendar.DAY_OF_MONTH, minOf(targetDayOfMonth, prevMax))
                    }
                }
                "Yearly" -> {
                    val ruleCal = Calendar.getInstance().apply { timeInMillis = rule.startDate }
                    set(Calendar.DAY_OF_MONTH, 1) // Neutralize 31-day overflow
                    set(Calendar.MONTH, ruleCal.get(Calendar.MONTH))
                    val maxDays = getActualMaximum(Calendar.DAY_OF_MONTH)
                    set(Calendar.DAY_OF_MONTH, minOf(ruleCal.get(Calendar.DAY_OF_MONTH), maxDays))
                    if (timeInMillis > nowMs) {
                        set(Calendar.DAY_OF_MONTH, 1) // Neutralize overflow before decrement
                        add(Calendar.YEAR, -1)
                        val prevMax = getActualMaximum(Calendar.DAY_OF_MONTH)
                        set(Calendar.DAY_OF_MONTH, minOf(ruleCal.get(Calendar.DAY_OF_MONTH), prevMax))
                    }
                }
            }
        }
    return cal.timeInMillis
}

/**
 * Evaluates whether a recurring transaction rule is due at [currentTimeMillis].
 *
 * Normalizes all timestamps to start-of-day before evaluating schedule guards.
 */
internal fun isDue(
    rule: RecurringTransaction,
    currentTimeMillis: Long,
): Boolean {
    val todayStartOfDay =
        Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    val ruleStartCal =
        Calendar.getInstance().apply {
            timeInMillis = rule.startDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    if (todayStartOfDay.before(ruleStartCal)) {
        return false
    }

    val lastRunDate = rule.lastRunDate ?: return true

    val lastRunCal =
        Calendar.getInstance().apply {
            timeInMillis = lastRunDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

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

    return !todayStartOfDay.before(nextDueDate)
}

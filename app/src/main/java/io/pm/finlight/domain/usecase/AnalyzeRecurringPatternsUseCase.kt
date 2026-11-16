package io.pm.finlight.domain.usecase

import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.RecurringPattern
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * UseCase to discover recurring transaction patterns from signed SMS transactions.
 */
interface AnalyzeRecurringPatternsUseCase {
    suspend operator fun invoke(
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): List<RecurringPattern>
}

/**
 * Default implementation of [AnalyzeRecurringPatternsUseCase] analyzing transactions over an observation
 * window (90 days) and detecting patterns meeting frequency and interval tolerance thresholds.
 */
class DefaultAnalyzeRecurringPatternsUseCase(
    private val transactionRepository: ITransactionRepository,
    private val recurringTransactionRepository: IRecurringTransactionRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : AnalyzeRecurringPatternsUseCase {
    companion object {
        private const val ANALYSIS_WINDOW_DAYS = 90L
        private const val MIN_OCCURRENCES_FOR_PATTERN = 3
        private const val WEEKLY_TOLERANCE_DAYS = 1
        private const val MONTHLY_TOLERANCE_DAYS = 3
    }

    override suspend operator fun invoke(
        currentTimeMillis: Long,
    ): List<RecurringPattern> =
        withContext(dispatcherProvider.io) {
            val detectedPatterns = mutableListOf<RecurringPattern>()

            val sinceDate = currentTimeMillis - TimeUnit.DAYS.toMillis(ANALYSIS_WINDOW_DAYS)
            val recentTransactions = transactionRepository.getTransactionsWithSignatureSince(sinceDate)

            for (transaction in recentTransactions) {
                val signature = transaction.smsSignature ?: continue
                val existingPattern = recurringTransactionRepository.getPatternBySignature(signature)

                if (existingPattern == null) {
                    recurringTransactionRepository.insertPattern(
                        RecurringPattern(
                            smsSignature = signature,
                            description = transaction.description,
                            amount = transaction.amount,
                            transactionType = transaction.transactionType,
                            accountId = transaction.accountId,
                            categoryId = transaction.categoryId,
                            occurrences = 1,
                            firstSeen = transaction.date,
                            lastSeen = transaction.date,
                        ),
                    )
                } else {
                    existingPattern.occurrences += 1
                    existingPattern.lastSeen = transaction.date
                    recurringTransactionRepository.updatePattern(existingPattern)
                }
            }

            val allPatterns = recurringTransactionRepository.getAllPatterns()
            for (pattern in allPatterns) {
                if (pattern.isDismissed) continue
                if (pattern.occurrences >= MIN_OCCURRENCES_FOR_PATTERN) {
                    val transactions = transactionRepository.getTransactionsBySignature(pattern.smsSignature)
                    if (transactions.size < MIN_OCCURRENCES_FOR_PATTERN) continue

                    val timestamps = transactions.map { it.date }.sorted()
                    val intervals = timestamps.zipWithNext { a, b -> TimeUnit.MILLISECONDS.toDays(b - a) }

                    val isWeekly = intervals.all { abs(it - 7) <= WEEKLY_TOLERANCE_DAYS }
                    val isMonthly = intervals.all { abs(it - 30) <= MONTHLY_TOLERANCE_DAYS }

                    if (isWeekly || isMonthly) {
                        detectedPatterns.add(pattern)
                    }
                }
            }

            detectedPatterns
        }
}

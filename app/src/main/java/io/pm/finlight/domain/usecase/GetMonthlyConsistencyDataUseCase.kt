package io.pm.finlight.domain.usecase

import android.util.Log
import io.pm.finlight.CalendarDayStatus
import io.pm.finlight.DailyTotal
import io.pm.finlight.SettingsRepository
import io.pm.finlight.SpendingStatus
import io.pm.finlight.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

class GetMonthlyConsistencyDataUseCase(
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
) {
    /**
     * Helper to check if cal1 is on a day *before* cal2, ignoring time.
     */
    private fun isBeforeDay(
        cal1: Calendar,
        cal2: Calendar,
    ): Boolean {
        return cal1.get(Calendar.YEAR) < cal2.get(Calendar.YEAR) ||
            (
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) < cal2.get(Calendar.DAY_OF_YEAR)
            )
    }

    /**
     * Generates the consistency data for a single month, based on that month's budget.
     * This is the single source of truth for all heatmap/calendar logic.
     */
    operator fun invoke(
        year: Int,
        month: Int,
    ): Flow<List<CalendarDayStatus>> {
        // Calculate start and end of the given month
        val monthStartCal =
            Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1) // Calendar.MONTH is 0-indexed
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val monthEndCal =
            (monthStartCal.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }
        val daysInMonth = monthStartCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Combine the three flows: budget, daily spending, first transaction date
        return combine(
            settingsRepository.getOverallBudgetForMonth(year, month),
            transactionRepository.getDailySpendingForDateRange(monthStartCal.timeInMillis, monthEndCal.timeInMillis),
            transactionRepository.getFirstTransactionDate(),
        ) { budget: Float?, dailyTotals: List<DailyTotal>, firstTransactionDate: Long? ->
            val firstDataCal = firstTransactionDate?.let { Calendar.getInstance().apply { timeInMillis = it } }
            val spendingMap = dailyTotals.associateBy({ it.date }, { it.totalAmount })
            val resultList = mutableListOf<CalendarDayStatus>()
            val dayIterator = (monthStartCal.clone() as Calendar)
            val today = Calendar.getInstance()

            if (budget == null) {
                // CASE 1: NO BUDGET SET (null)
                // All past days are NO_DATA (gray).
                for (i in 1..daysInMonth) {
                    dayIterator.set(Calendar.DAY_OF_MONTH, i)
                    val date = dayIterator.time

                    if (dayIterator.after(today) || (firstDataCal != null && isBeforeDay(dayIterator, firstDataCal))) {
                        resultList.add(CalendarDayStatus(date, SpendingStatus.NO_DATA, 0L, 0L))
                    } else {
                        // Any past day with NO BUDGET is NO_DATA, even if there was no spending.
                        val dateKey = String.format(Locale.ROOT, "%d-%02d-%02d", year, month, i)
                        val amountSpent = (spendingMap[dateKey] ?: 0.0).roundToLong()
                        val status = SpendingStatus.NO_DATA
                        resultList.add(CalendarDayStatus(date, status, amountSpent, 0L))
                    }
                }
            } else {
                // CASE 2: A BUDGET IS SET (e.g., 0f or 145000f)
                var cumulativeSpending = 0.0
                val totalBudget = budget.toDouble()

                for (i in 1..daysInMonth) {
                    dayIterator.set(Calendar.DAY_OF_MONTH, i)
                    val date = dayIterator.time

                    if (dayIterator.after(today) || (firstDataCal != null && isBeforeDay(dayIterator, firstDataCal))) {
                        resultList.add(CalendarDayStatus(date, SpendingStatus.NO_DATA, 0L, 0L))
                        continue
                    }

                    val remainingBudget = totalBudget - cumulativeSpending
                    val remainingDays = (daysInMonth - i + 1).coerceAtLeast(1)
                    val safeToSpend = if (remainingBudget > 0) (remainingBudget / remainingDays).roundToLong() else 0L

                    val dateKey = String.format(Locale.ROOT, "%d-%02d-%02d", year, month, i)
                    val amountSpent = (spendingMap[dateKey] ?: 0.0)
                    val amountSpentLong = amountSpent.roundToLong()

                    val status =
                        when {
                            amountSpentLong == 0L && safeToSpend == 0L -> SpendingStatus.WITHIN_LIMIT // Met 0 budget (blue)
                            amountSpentLong == 0L && safeToSpend > 0L -> SpendingStatus.NO_SPEND // No spend on a day with a budget (green)
                            amountSpentLong > 0L && safeToSpend == 0L -> SpendingStatus.OVER_LIMIT // Spent > 0 on a 0 budget (red)
                            amountSpentLong > safeToSpend -> SpendingStatus.OVER_LIMIT // Spent > budget (red)
                            else -> SpendingStatus.WITHIN_LIMIT // Spent <= budget (and not 0) (blue)
                        }
                    Log.d("HeatmapDebug", "Date: $dateKey, Spent: $amountSpentLong, Threshold: $safeToSpend, Status: $status")
                    resultList.add(CalendarDayStatus(date, status, amountSpentLong, safeToSpend))
                    cumulativeSpending += amountSpent
                }
            }
            resultList
        }.flowOn(Dispatchers.Default)
    }
}

package io.pm.finlight

import io.pm.finlight.utils.FormatUtils
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A data class to hold a Calendar instance for a specific month
 * and the total amount spent during that month.
 *
 * @param calendar The Calendar object representing the month.
 * @param totalSpent The total expenses for that month.
 */
data class MonthlySummaryItem(val calendar: Calendar, val totalSpent: Double) {
    companion object {
        fun fromTrends(
            trends: List<MonthlyTrend>,
            startDate: Long,
        ): List<MonthlySummaryItem> {
            val dateFormat = FormatUtils.getFormatter("yyyy-MM", Locale.getDefault())
            val monthMap =
                trends.associate {
                    val cal = Calendar.getInstance().apply { time = dateFormat.parse(it.monthYear) ?: Date() }
                    (cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)) to it.totalExpenses
                }
            val monthList = mutableListOf<MonthlySummaryItem>()
            val startCal = Calendar.getInstance().apply { timeInMillis = startDate }
            startCal.set(Calendar.DAY_OF_MONTH, 1)
            val endCal = Calendar.getInstance()
            while (startCal.before(endCal) || (startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) && startCal.get(Calendar.MONTH) == endCal.get(Calendar.MONTH))) {
                val key = startCal.get(Calendar.YEAR) * 100 + startCal.get(Calendar.MONTH)
                monthList.add(MonthlySummaryItem(calendar = startCal.clone() as Calendar, totalSpent = monthMap[key] ?: 0.0))
                startCal.add(Calendar.MONTH, 1)
            }
            return monthList.reversed()
        }
    }
}

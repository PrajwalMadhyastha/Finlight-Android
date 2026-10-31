package io.pm.finlight.data.model

import io.pm.finlight.MonthlySummaryItem
import io.pm.finlight.MonthlyTrend
import io.pm.finlight.utils.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class MonthlySummaryItemTest {
    @Test
    fun `data class equals hashCode and copy behave correctly`() {
        val cal1 = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1) }
        val cal2 = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1) }
        val item1 = MonthlySummaryItem(cal1, 100.0)
        val item2 = MonthlySummaryItem(cal2, 100.0)
        val item3 = item1.copy(totalSpent = 200.0)

        assertEquals(item1, item2)
        assertEquals(item1.hashCode(), item2.hashCode())
        assertNotEquals(item1, item3)
        assertEquals(200.0, item3.totalSpent, 0.001)
        assertTrue(item1.toString().contains("totalSpent=100.0"))
    }

    @Test
    fun `fromTrends populates months correctly from startDate to current date`() {
        val currentCal = Calendar.getInstance()
        val currentYear = currentCal.get(Calendar.YEAR)
        val currentMonth = currentCal.get(Calendar.MONTH)

        // Set startCal to 2 months ago
        val startCal =
            (currentCal.clone() as Calendar).apply {
                add(Calendar.MONTH, -2)
                set(Calendar.DAY_OF_MONTH, 1)
            }
        val startYear = startCal.get(Calendar.YEAR)
        val startMonth = startCal.get(Calendar.MONTH)

        val formatter = FormatUtils.getFormatter("yyyy-MM", Locale.getDefault())
        val monthYearStr = formatter.format(startCal.time)

        val trends =
            listOf(
                MonthlyTrend(monthYear = monthYearStr, totalIncome = 1000.0, totalExpenses = 450.0),
            )

        val result = MonthlySummaryItem.fromTrends(trends, startCal.timeInMillis)

        // Result is in reversed order (latest month first)
        assertTrue(result.isNotEmpty())
        assertEquals(3, result.size)

        // First item in reversed list should be the current month
        val latestItem = result.first()
        assertEquals(currentYear, latestItem.calendar.get(Calendar.YEAR))
        assertEquals(currentMonth, latestItem.calendar.get(Calendar.MONTH))
        assertEquals(0.0, latestItem.totalSpent, 0.001)

        // Last item in reversed list should be the start month with 450.0 totalExpenses
        val earliestItem = result.last()
        assertEquals(startYear, earliestItem.calendar.get(Calendar.YEAR))
        assertEquals(startMonth, earliestItem.calendar.get(Calendar.MONTH))
        assertEquals(450.0, earliestItem.totalSpent, 0.001)
    }

    @Test
    fun `fromTrends maps multiple monthly trends correctly`() {
        val currentCal = Calendar.getInstance()
        val prevCal =
            (currentCal.clone() as Calendar).apply {
                add(Calendar.MONTH, -1)
                set(Calendar.DAY_OF_MONTH, 1)
            }
        val formatter = FormatUtils.getFormatter("yyyy-MM", Locale.getDefault())
        val currentMonthStr = formatter.format(currentCal.time)
        val prevMonthStr = formatter.format(prevCal.time)

        val trends =
            listOf(
                MonthlyTrend(monthYear = currentMonthStr, totalIncome = 1500.0, totalExpenses = 750.0),
                MonthlyTrend(monthYear = prevMonthStr, totalIncome = 1200.0, totalExpenses = 600.0),
            )

        val result = MonthlySummaryItem.fromTrends(trends, prevCal.timeInMillis)

        assertEquals(2, result.size)
        assertEquals(750.0, result[0].totalSpent, 0.001)
        assertEquals(600.0, result[1].totalSpent, 0.001)
    }

    @Test
    fun `fromTrends returns empty list when startDate is in the future`() {
        val futureCal =
            Calendar.getInstance().apply {
                add(Calendar.MONTH, 2)
            }

        val result = MonthlySummaryItem.fromTrends(emptyList(), futureCal.timeInMillis)

        assertTrue(result.isEmpty())
    }
}

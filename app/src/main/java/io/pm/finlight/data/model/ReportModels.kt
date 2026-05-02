package io.pm.finlight

import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.PieData

/**
 * Enum to represent the selectable time periods on the Reports screen.
 */
enum class ReportPeriod(val displayName: String) {
    WEEK("This Week"),
    MONTH("This Month"),
    QUARTER("3 Months"),
    ALL_TIME("All Time"),
}

/**
 * Data class to hold key insights calculated for the selected period.
 */
data class ReportInsights(
    val percentageChange: Int?,
    val topCategory: CategorySpending?,
)

/**
 * Data class to hold all the computed data for the reports screen.
 */
data class ReportScreenData(
    val pieData: PieData?,
    val trendData: Pair<BarData, List<String>>?,
    val periodTitle: String,
    val insights: ReportInsights?,
)

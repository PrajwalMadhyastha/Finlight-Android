package io.pm.finlight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener

@Composable
fun TimePickerDialog(
    title: String = "Select Time",
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ChartLegend(pieData: PieData?) {
    val dataSet = pieData?.dataSet as? PieDataSet ?: return

    Column {
        for (i in 0 until dataSet.entryCount) {
            val entry = dataSet.getEntryForIndex(i)
            val color = dataSet.getColor(i)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(color)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${entry.label} - ₹${"%.2f".format(entry.value)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GroupedBarChart(
    chartData: Pair<BarData, List<String>>,
    onBarClick: ((Entry) -> Unit)? = null
) {
    val (barData, labels) = chartData
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val legendColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val customGridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f).toArgb()

    AndroidView(
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = true
                legend.textSize = 12f
                setDrawGridBackground(false)
                setDrawBarShadow(false)
                setDrawValueAboveBar(false) // Disable values above bars to prevent overlap

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    setDrawAxisLine(false)
                    granularity = 1f
                    textSize = 11f
                }

                axisLeft.apply {
                    axisMinimum = 0f
                    setDrawGridLines(true)
                    gridColor = customGridColor
                    gridLineWidth = 0.5f
                    textSize = 11f
                    setDrawAxisLine(false)
                    // Use custom formatter for Y-axis labels
                    valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return when {
                                value >= 10000000 -> String.format("%.1fCr", value / 10000000)
                                value >= 100000 -> String.format("%.1fL", value / 100000)
                                value >= 1000 -> String.format("%.1fK", value / 1000)
                                else -> value.toInt().toString()
                            }
                        }
                    }
                }

                axisRight.isEnabled = false
                
                // Enable dragging/scrolling
                isDragEnabled = true
                setScaleEnabled(false)
                setPinchZoom(false)

                // Add click listener
                if (onBarClick != null) {
                    setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                            e?.let { onBarClick(it) }
                        }
                        override fun onNothingSelected() {}
                    })
                }
            }
        },
        update = { chart ->
            val barWidth = 0.25f
            val barSpace = 0.05f
            val groupSpace = 0.4f
            barData.barWidth = barWidth

            // Disable value labels on bars to prevent overlap
            barData.dataSets.forEach { dataSet ->
                dataSet.setDrawValues(false)
            }

            chart.data = barData
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            chart.xAxis.axisMinimum = 0f
            chart.xAxis.axisMaximum = labels.size.toFloat()
            chart.xAxis.setCenterAxisLabels(true)

            chart.legend.textColor = legendColor
            chart.xAxis.textColor = textColor
            chart.axisLeft.textColor = textColor

            chart.groupBars(0f, groupSpace, barSpace)
            
            // Limit visible range to 5 groups and scroll to end
            chart.setVisibleXRangeMaximum(5f)
            chart.moveViewToX(labels.size.toFloat())
            
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp) // Increased from 250dp for better spacing
    )
}

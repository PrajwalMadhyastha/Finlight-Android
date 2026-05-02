// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/ConsistencyCalendarLegend.kt
// REASON: NEW FILE - This composable defines the legend for the spending
// consistency heatmap, mapping each color to its corresponding status.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

@Composable
fun ConsistencyCalendarLegend() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), label = "No Data")
        LegendItem(color = Color(0xFF39D353), label = "No Spend")
        LegendItem(color = lerp(Color(0xFFACD5F2), Color(0xFF006DAB), 0.5f), label = "In Budget")
        LegendItem(color = lerp(Color(0xFFF87171), Color(0xFFB91C1C), 0.5f), label = "Over Budget")
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

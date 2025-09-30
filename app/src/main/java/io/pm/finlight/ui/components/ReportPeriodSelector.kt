// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/ReportPeriodSelector.kt
// REASON: NEW FILE - This new, reusable component creates an interactive header
// for report screens. It clearly displays the current time period and provides
// clickable arrows for navigation, making the swipe gesture more discoverable
// and accessible.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.pm.finlight.data.model.TimePeriod
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReportPeriodSelector(
    timePeriod: TimePeriod,
    selectedDate: Date,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val (title, subtitle) = remember(timePeriod, selectedDate) {
        getFormattedDateStrings(timePeriod, selectedDate)
    }

    GlassPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous Period")
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next Period")
            }
        }
    }
}

private fun getFormattedDateStrings(timePeriod: TimePeriod, date: Date): Pair<String, String> {
    val cal = Calendar.getInstance().apply { time = date }
    return when (timePeriod) {
        TimePeriod.DAILY -> {
            val titleFormat = SimpleDateFormat("MMMM d", Locale.getDefault())
            val subtitleFormat = SimpleDateFormat("EEEE", Locale.getDefault())
            titleFormat.format(date) to subtitleFormat.format(date)
        }
        TimePeriod.WEEKLY -> {
            val startOfWeek = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
            val endOfWeek = cal
            val startFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            val endFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            "This Week" to "${startFormat.format(startOfWeek.time)} - ${endFormat.format(endOfWeek.time)}"
        }
        TimePeriod.MONTHLY -> {
            val format = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            format.format(date) to "Full month summary"
        }
        TimePeriod.YEARLY -> {
            val format = SimpleDateFormat("yyyy", Locale.getDefault())
            format.format(date) to "Full year summary"
        }
    }
}
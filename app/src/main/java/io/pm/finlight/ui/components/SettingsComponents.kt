// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/SettingsComponents.kt
// REASON: REFACTOR - The ListItem components have been updated to use a
// transparent container color. This ensures they blend seamlessly when placed
// inside a GlassPanel, allowing the frosted glass effect to show through and
// creating a visually consistent settings screen.
// FIX: Removed the duplicate TimePickerDialog definition to resolve the
// "Conflicting overloads" compilation error.
// BUG FIX - The AlertDialogs for the time pickers now determine their color
// based on the app's MaterialTheme, not the system theme. This ensures they
// correctly follow the selected app theme (e.g., Aurora, Daybreak) instead of
// defaulting to the system's light/dark mode.
// BUG FIX - Ensured the Switch component is always present in the
// SettingsToggleItem's trailingContent, fixing an issue where it could disappear.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import java.util.*

// Helper function to determine if a color is 'dark' based on luminance.
private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent, // Make transparent to show GlassPanel behind
            headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            supportingColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            leadingIconColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        )
    )
}

@Composable
fun SettingsActionItem(
    text: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(text) },
        supportingContent = { subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent, // Make transparent to show GlassPanel behind
            headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            supportingColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            leadingIconColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportTimePicker(
    initialDay: Int,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit
) {
    var selectedDay by remember { mutableStateOf(initialDay) }
    val timePickerState = rememberTimePickerState(initialHour, initialMinute, false)
    val days = (1..7).map {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, it) }
        Pair(it, cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()))
    }

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Weekly Report Time") },
        text = {
            Column {
                Text("Day of the Week", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.chunked(4).forEach { rowDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowDays.forEach { (dayInt, dayName) ->
                                val isSelected = dayInt == selectedDay
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium,
                                    onClick = { selectedDay = dayInt },
                                    colors = if (isSelected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonColors(),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text(dayName)
                                }
                            }
                            if (rowDays.size < 4) {
                                Spacer(modifier = Modifier.weight(4f - rowDays.size))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDay, timePickerState.hour, timePickerState.minute) }) {
                Text("Set Time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = popupContainerColor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportTimePicker(
    initialDay: Int,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit
) {
    var selectedDay by remember { mutableStateOf(initialDay) }
    val timePickerState = rememberTimePickerState(initialHour, initialMinute, false)
    var isDayPickerExpanded by remember { mutableStateOf(false) }

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Monthly Report Time") },
        text = {
            Column {
                Text("Day of the Month", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { isDayPickerExpanded = !isDayPickerExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Day: $selectedDay")
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = if (isDayPickerExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Toggle day picker"
                    )
                }
                AnimatedVisibility(visible = isDayPickerExpanded) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 52.dp),
                        modifier = Modifier.heightIn(max = 240.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items((1..28).toList()) { day ->
                            val isSelected = day == selectedDay
                            OutlinedButton(
                                onClick = {
                                    selectedDay = day
                                    isDayPickerExpanded = false
                                },
                                shape = CircleShape,
                                colors = if (isSelected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonColors(),
                                modifier = Modifier.size(48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("$day")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDay, timePickerState.hour, timePickerState.minute) }) {
                Text("Set Time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = popupContainerColor
    )
}

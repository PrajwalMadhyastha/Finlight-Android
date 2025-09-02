// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/CurrencyTravelScreen.kt
// REASON: FEATURE - Unified Travel Mode. The UI has been completely redesigned to
// support the new unified travel mode. It now features a single "Enable" switch,
// a mandatory "Trip Name" field for auto-tagging, a "Domestic/International"
// selector, and conditionally displays currency fields only for international trips.
// =================================================================================
package io.pm.finlight.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.utils.CurrencyHelper
import io.pm.finlight.utils.CurrencyInfo
import io.pm.finlight.CurrencyViewModel
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import java.text.SimpleDateFormat
import java.util.*

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyTravelScreen(
    navController: NavController,
    viewModel: CurrencyViewModel = viewModel()
) {
    val homeCurrencyCode by viewModel.homeCurrency.collectAsState()
    val travelSettings by viewModel.travelModeSettings.collectAsState()
    val context = LocalContext.current

    // UI State
    var isTravelModeEnabled by remember(travelSettings) { mutableStateOf(travelSettings?.isEnabled ?: false) }
    var tripName by remember(travelSettings) { mutableStateOf(travelSettings?.tripName ?: "") }
    var tripType by remember(travelSettings) { mutableStateOf(travelSettings?.tripType ?: TripType.DOMESTIC) }
    var selectedCurrency by remember(travelSettings) { mutableStateOf(CurrencyHelper.getCurrencyInfo(travelSettings?.currencyCode)) }
    var conversionRate by remember(travelSettings) { mutableStateOf(travelSettings?.conversionRate?.toString() ?: "") }
    var startDate by remember(travelSettings) { mutableStateOf(travelSettings?.startDate) }
    var endDate by remember(travelSettings) { mutableStateOf(travelSettings?.endDate) }

    // Dialog visibility
    var showHomeCurrencyPicker by remember { mutableStateOf(false) }
    var showTravelCurrencyPicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val isSaveEnabled = if (isTravelModeEnabled) {
        tripName.isNotBlank() &&
                startDate != null &&
                endDate != null &&
                (tripType == TripType.DOMESTIC || (selectedCurrency != null && (conversionRate.toFloatOrNull() ?: 0f) > 0f))
    } else {
        true // Allow saving if the mode is being disabled
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Currency & Travel") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = "Home Currency") {
                    ListItem(
                        headlineContent = { Text("Default Currency") },
                        supportingContent = { Text("Used for all reports and budgets") },
                        trailingContent = {
                            TextButton(onClick = { showHomeCurrencyPicker = true }) {
                                Text("${CurrencyHelper.getCurrencyInfo(homeCurrencyCode)?.currencyCode ?: homeCurrencyCode} (${CurrencyHelper.getCurrencySymbol(homeCurrencyCode)})")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            item {
                SettingsSection(title = "Travel Mode") {
                    ListItem(
                        headlineContent = { Text("Enable Travel Mode") },
                        supportingContent = { Text("Track expenses for a specific trip with auto-tagging.") },
                        trailingContent = {
                            Switch(
                                checked = isTravelModeEnabled,
                                onCheckedChange = {
                                    isTravelModeEnabled = it
                                    if (!it) {
                                        viewModel.disableTravelMode()
                                        Toast.makeText(context, "Travel Mode Disabled", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    AnimatedVisibility(visible = isTravelModeEnabled) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            ListItem(
                                headlineContent = {
                                    OutlinedTextField(
                                        value = tripName,
                                        onValueChange = { tripName = it },
                                        label = { Text("Trip Name / Tag*") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            ListItem(
                                headlineContent = { Text("Trip Type") },
                                trailingContent = {
                                    SingleChoiceSegmentedButtonRow {
                                        SegmentedButton(
                                            selected = tripType == TripType.DOMESTIC,
                                            onClick = { tripType = TripType.DOMESTIC },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                        ) { Text("Domestic") }
                                        SegmentedButton(
                                            selected = tripType == TripType.INTERNATIONAL,
                                            onClick = { tripType = TripType.INTERNATIONAL },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                        ) { Text("International") }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            AnimatedVisibility(visible = tripType == TripType.INTERNATIONAL) {
                                Column {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                    ListItem(
                                        headlineContent = { Text("Foreign Currency") },
                                        trailingContent = {
                                            TextButton(onClick = { showTravelCurrencyPicker = true }) {
                                                Text(selectedCurrency?.currencyCode ?: "Select")
                                            }
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                    ListItem(
                                        headlineContent = {
                                            Column {
                                                Text("Conversion Rate")
                                                Text(
                                                    "1 ${selectedCurrency?.currencyCode ?: "Foreign"} = ? ${homeCurrencyCode}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        trailingContent = {
                                            OutlinedTextField(
                                                value = conversionRate,
                                                onValueChange = { conversionRate = it.filter { c -> c.isDigit() || c == '.' } },
                                                modifier = Modifier.width(100.dp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                label = { Text(homeCurrencyCode) }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            ListItem(
                                headlineContent = { Text("Trip Start Date") },
                                trailingContent = {
                                    val formatter = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }
                                    TextButton(onClick = { showStartDatePicker = true }) {
                                        Text(startDate?.let { formatter.format(Date(it)) } ?: "Select")
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            ListItem(
                                headlineContent = { Text("Trip End Date") },
                                trailingContent = {
                                    val formatter = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }
                                    TextButton(onClick = { showEndDatePicker = true }) {
                                        Text(endDate?.let { formatter.format(Date(it)) } ?: "Select")
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }

            item {
                if (isTravelModeEnabled) {
                    Button(
                        onClick = {
                            val settings = TravelModeSettings(
                                isEnabled = true,
                                tripName = tripName,
                                tripType = tripType,
                                startDate = startDate!!,
                                endDate = endDate!!,
                                currencyCode = if (tripType == TripType.INTERNATIONAL) selectedCurrency?.currencyCode else null,
                                conversionRate = if (tripType == TripType.INTERNATIONAL) conversionRate.toFloatOrNull() else null
                            )
                            viewModel.saveTravelModeSettings(settings)
                            Toast.makeText(context, "Travel Mode settings saved!", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        },
                        enabled = isSaveEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Travel Settings")
                    }
                }
            }
        }
    }

    if (showHomeCurrencyPicker) {
        CurrencyPickerDialog(
            title = "Select Home Currency",
            onDismiss = { showHomeCurrencyPicker = false },
            onCurrencySelected = {
                viewModel.saveHomeCurrency(it.currencyCode)
                showHomeCurrencyPicker = false
            }
        )
    }

    if (showTravelCurrencyPicker) {
        CurrencyPickerDialog(
            title = "Select Travel Currency",
            onDismiss = { showTravelCurrencyPicker = false },
            onCurrencySelected = {
                selectedCurrency = it
                showTravelCurrencyPicker = false
            }
        )
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = datePickerState.selectedDateMillis
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate ?: startDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = datePickerState.selectedDateMillis
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        GlassPanel {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun CurrencyPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onCurrencySelected: (CurrencyInfo) -> Unit
) {
    val isThemeDark = MaterialTheme.colorScheme.surface.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn {
                    items(CurrencyHelper.commonCurrencies.size) { index ->
                        val currency = CurrencyHelper.commonCurrencies[index]
                        ListItem(
                            headlineContent = { Text("${currency.countryName} (${currency.currencyCode})") },
                            trailingContent = { Text(currency.currencySymbol) },
                            modifier = Modifier.clickable { onCurrencySelected(currency) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = popupContainerColor
    )
}

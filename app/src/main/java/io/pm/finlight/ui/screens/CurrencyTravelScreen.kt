// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/CurrencyTravelScreen.kt
// REASON: FIX - The state initialization logic has been completely rewritten to
// correctly and separately handle the "edit mode" (from `tripToEdit`) and
// "active mode" (from `activeTravelSettings`). This resolves the type mismatch
// build error and ensures the UI populates correctly in all scenarios.
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
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.CurrencyViewModel
import java.text.SimpleDateFormat
import java.util.*

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyTravelScreen(
    navController: NavController,
    tripId: Int?,
    viewModel: CurrencyViewModel = viewModel()
) {
    val isEditMode = tripId != null
    val homeCurrencyCode by viewModel.homeCurrency.collectAsState()
    val activeTravelSettings by viewModel.travelModeSettings.collectAsState()
    val tripToEdit by viewModel.tripToEdit.collectAsState()
    val context = LocalContext.current

    // Load trip data if in edit mode
    LaunchedEffect(tripId) {
        if (isEditMode && tripId != null) {
            viewModel.loadTripForEditing(tripId)
        }
    }

    // --- FIX: Separate state initialization logic to avoid type conflicts ---
    var tripName by remember { mutableStateOf("") }
    var tripType by remember { mutableStateOf(TripType.DOMESTIC) }
    var selectedCurrency by remember { mutableStateOf<CurrencyInfo?>(null) }
    var conversionRate by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var showCreateNewTripView by remember(activeTravelSettings, isEditMode) { mutableStateOf(activeTravelSettings == null && !isEditMode) }


    // Effect to populate the UI state when the relevant data source changes
    LaunchedEffect(tripToEdit, activeTravelSettings, isEditMode) {
        val source = if (isEditMode) {
            tripToEdit?.let {
                // Adapt TripWithStats to a common structure
                TravelModeSettings(
                    isEnabled = true,
                    tripName = it.tripName,
                    tripType = it.tripType,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    currencyCode = it.currencyCode,
                    conversionRate = it.conversionRate
                )
            }
        } else {
            activeTravelSettings
        }

        // Reset to defaults if source is null
        tripName = source?.tripName ?: ""
        tripType = source?.tripType ?: TripType.DOMESTIC
        selectedCurrency = CurrencyHelper.getCurrencyInfo(source?.currencyCode)
        conversionRate = source?.conversionRate?.toString() ?: ""
        startDate = source?.startDate
        endDate = source?.endDate
    }


    // Dialog visibility states
    var showHomeCurrencyPicker by remember { mutableStateOf(false) }
    var showTravelCurrencyPicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showCancelConfirmation by remember { mutableStateOf(false) }

    val isSaveEnabled = tripName.isNotBlank() &&
            startDate != null &&
            endDate != null &&
            (tripType == TripType.DOMESTIC || (selectedCurrency != null && (conversionRate.toFloatOrNull() ?: 0f) > 0f))

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
                if (showCreateNewTripView) {
                    Button(
                        onClick = { showCreateNewTripView = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create New Trip Plan")
                    }
                }
            }

            item {
                AnimatedVisibility(visible = !showCreateNewTripView) {
                    val sectionTitle = when {
                        isEditMode -> "Edit Trip Plan"
                        activeTravelSettings != null -> "Active Trip Plan"
                        else -> "New Trip Plan"
                    }
                    SettingsSection(title = sectionTitle) {
                        TripSettingsForm(
                            tripName = tripName, onTripNameChange = { tripName = it },
                            tripType = tripType, onTripTypeChange = { tripType = it },
                            selectedCurrency = selectedCurrency, onSelectCurrencyClick = { showTravelCurrencyPicker = true },
                            conversionRate = conversionRate, onConversionRateChange = { conversionRate = it.filter { c -> c.isDigit() || c == '.' } },
                            homeCurrencyCode = homeCurrencyCode,
                            startDate = startDate, onStartDateClick = { showStartDatePicker = true },
                            endDate = endDate, onEndDateClick = { showEndDatePicker = true }
                        )
                    }
                }
            }

            item {
                if (!showCreateNewTripView) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (activeTravelSettings != null && !isEditMode) {
                            Button(
                                onClick = {
                                    viewModel.completeTrip()
                                    Toast.makeText(context, "Trip Completed! Future transactions will not be tagged.", Toast.LENGTH_LONG).show()
                                    navController.popBackStack()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Mark Trip as Completed")
                            }
                        }

                        Button(
                            onClick = {
                                val settings = TravelModeSettings(
                                    isEnabled = true, tripName = tripName, tripType = tripType,
                                    startDate = startDate!!, endDate = endDate!!,
                                    currencyCode = if (tripType == TripType.INTERNATIONAL) selectedCurrency?.currencyCode else null,
                                    conversionRate = if (tripType == TripType.INTERNATIONAL) conversionRate.toFloatOrNull() else null
                                )
                                viewModel.saveTravelModeSettings(settings)
                                val toastMessage = if (activeTravelSettings == null && !isEditMode) "New travel plan saved!" else "Trip details updated!"
                                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            },
                            enabled = isSaveEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (activeTravelSettings == null && !isEditMode) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                        ) {
                            val buttonText = when {
                                isEditMode -> "Update Trip"
                                activeTravelSettings == null -> "Save and Activate Plan"
                                else -> "Save Changes"
                            }
                            Text(buttonText)
                        }

                        if (activeTravelSettings != null && !isEditMode) {
                            TextButton(
                                onClick = { showCancelConfirmation = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel Trip & Remove Data", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Cancel Trip?") },
            text = { Text("This will permanently remove the trip from your history and untag all associated transactions. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        activeTravelSettings?.let { viewModel.cancelTrip(it) }
                        showCancelConfirmation = false
                        Toast.makeText(context, "Trip cancelled and data removed.", Toast.LENGTH_LONG).show()
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Confirm Cancellation") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmation = false }) { Text("Nevermind") }
            }
        )
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripSettingsForm(
    tripName: String, onTripNameChange: (String) -> Unit,
    tripType: TripType, onTripTypeChange: (TripType) -> Unit,
    selectedCurrency: CurrencyInfo?, onSelectCurrencyClick: () -> Unit,
    conversionRate: String, onConversionRateChange: (String) -> Unit,
    homeCurrencyCode: String,
    startDate: Long?, onStartDateClick: () -> Unit,
    endDate: Long?, onEndDateClick: () -> Unit
) {
    Column {
        ListItem(
            headlineContent = {
                OutlinedTextField(
                    value = tripName,
                    onValueChange = onTripNameChange,
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
                        onClick = { onTripTypeChange(TripType.DOMESTIC) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Domestic") }
                    SegmentedButton(
                        selected = tripType == TripType.INTERNATIONAL,
                        onClick = { onTripTypeChange(TripType.INTERNATIONAL) },
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
                        TextButton(onClick = onSelectCurrencyClick) {
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
                                "1 ${selectedCurrency?.currencyCode ?: "Foreign"} = ? $homeCurrencyCode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingContent = {
                        OutlinedTextField(
                            value = conversionRate,
                            onValueChange = onConversionRateChange,
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
                TextButton(onClick = onStartDateClick) {
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
                TextButton(onClick = onEndDateClick) {
                    Text(endDate?.let { formatter.format(Date(it)) } ?: "Select")
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
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
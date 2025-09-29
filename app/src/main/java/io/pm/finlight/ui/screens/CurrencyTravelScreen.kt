// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/CurrencyTravelScreen.kt
// REASON: FIX - The logic for handling date selection has been corrected. The
// `startDate` is now explicitly set to the beginning of the selected day (00:00),
// and the `endDate` is set to the very end (23:59:59). This resolves the
// critical bug where transactions occurring on the last day of a trip were
// not being tagged.
// FIX: The date conversion logic now correctly uses java.time.Instant and
// ZoneId.systemDefault() to make the start/end timestamps timezone-agnostic.
// This ensures that trip date boundaries are stored in UTC, fixing tagging
// issues for users who travel across timezones.
// FIX: Added a DisposableEffect to call `viewModel.clearTripToEdit()` when the
// screen is left. This prevents stale data from a historic trip edit session
// from appearing when creating a new trip.
// REFACTOR: The screen now displays the list of historic trips directly,
// creating a unified "Travel Hub" and removing the need for a separate screen.
// FIX: The screen layout has been restructured to always show the active trip
// form (if a trip is active) AND the travel history list below it, resolving
// the issue where the history was incorrectly hidden.
// FIX: Restructured the UI to always show a trip management form (either for
// an active trip or to create a new one), resolving the bug where the user
// could not initiate a new trip. The "Create New Trip" button has been removed
// in favor of this persistent form.
// FIX (Theming) - All AlertDialogs and DatePickerDialogs on this screen now
// correctly derive their background color from the app's MaterialTheme, ensuring
// text is always legible in both light and dark modes.
// =================================================================================
package io.pm.finlight.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.utils.CurrencyHelper
import io.pm.finlight.utils.CurrencyInfo
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.CurrencyViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
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
    val historicTrips by viewModel.historicTrips.collectAsState()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearTripToEdit()
        }
    }


    // Load trip data if in edit mode
    LaunchedEffect(tripId) {
        if (isEditMode && tripId != null) {
            viewModel.loadTripForEditing(tripId)
        }
    }

    var tripName by remember { mutableStateOf("") }
    var tripType by remember { mutableStateOf(TripType.DOMESTIC) }
    var selectedCurrency by remember { mutableStateOf<CurrencyInfo?>(null) }
    var conversionRate by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var tripToDelete by remember { mutableStateOf<TripWithStats?>(null) }


    // Effect to populate the UI state when the relevant data source changes
    LaunchedEffect(tripToEdit, activeTravelSettings, isEditMode) {
        val source = if (isEditMode) {
            tripToEdit?.let {
                TravelModeSettings(
                    isEnabled = true, tripName = it.tripName, tripType = it.tripType,
                    startDate = it.startDate, endDate = it.endDate,
                    currencyCode = it.currencyCode, conversionRate = it.conversionRate
                )
            }
        } else {
            activeTravelSettings
        }

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

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Currency & Travel") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    HelpActionIcon(helpKey = "currency_travel_settings")
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
                val sectionTitle = when {
                    isEditMode -> "Edit Trip Plan"
                    activeTravelSettings != null -> "Active Trip Plan"
                    else -> "Create New Trip Plan"
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


            item {
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
                            if (isEditMode) {
                                viewModel.updateHistoricTrip(settings)
                            } else {
                                viewModel.saveActiveTravelPlan(settings)
                            }

                            val toastMessage = if (activeTravelSettings == null && !isEditMode) "New travel plan saved!" else "Trip details updated!"
                            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        },
                        enabled = isSaveEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (activeTravelSettings == null && !isEditMode) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) {
                        val buttonText = when {
                            isEditMode -> "Update Trip Details"
                            activeTravelSettings == null -> "Save and Activate Plan"
                            else -> "Save Changes to Active Plan"
                        }
                        Text(buttonText)
                    }

                    if (activeTravelSettings != null && !isEditMode) {
                        TextButton(
                            onClick = { showCancelConfirmation = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel Active Trip", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (historicTrips.isNotEmpty()) {
                item {
                    SettingsSection("Travel History") {
                        Column {
                            historicTrips.forEach { trip ->
                                HistoricTripItem(
                                    trip = trip,
                                    onClick = { navController.navigate("trip_detail/${trip.tripId}/${trip.tagId}") },
                                    onEditClick = { navController.navigate("currency_travel_settings?tripId=${trip.tripId}") },
                                    onDeleteClick = { tripToDelete = trip }
                                )
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
            containerColor = popupContainerColor,
            title = { Text("Cancel Trip?") },
            text = { Text("This will untag all transactions for this specific trip instance. The trip will be removed from your history. This cannot be undone.") },
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

    if (tripToDelete != null) {
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            containerColor = popupContainerColor,
            title = { Text("Delete Trip?") },
            text = { Text("Are you sure you want to delete '${tripToDelete?.tripName}'? This will untag ${tripToDelete?.transactionCount} transaction(s). This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrip(tripToDelete!!.tripId, tripToDelete!!.tagId)
                        tripToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { tripToDelete = null }) { Text("Cancel") }
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
                    datePickerState.selectedDateMillis?.let { selectedMillis ->
                        val localDate = Instant.ofEpochMilli(selectedMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        val startOfDayInLocalZone = localDate.atStartOfDay(ZoneId.systemDefault())
                        startDate = startOfDayInLocalZone.toInstant().toEpochMilli()
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") } },
            colors = DatePickerDefaults.colors(containerColor = popupContainerColor)
        ) { DatePicker(state = datePickerState) }
    }
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate ?: startDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedMillis ->
                        val localDate = Instant.ofEpochMilli(selectedMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        val endOfDayInLocalZone = localDate.atTime(23, 59, 59, 999_999_999)
                            .atZone(ZoneId.systemDefault())
                        endDate = endOfDayInLocalZone.toInstant().toEpochMilli()
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") } },
            colors = DatePickerDefaults.colors(containerColor = popupContainerColor)
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
    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
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

@Composable
private fun HistoricTripItem(
    modifier: Modifier = Modifier,
    trip: TripWithStats,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }

    GlassPanel(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.tripName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${dateFormat.format(Date(trip.startDate))} - ${dateFormat.format(Date(trip.endDate))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, "Edit Trip", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, "Delete Trip", tint = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Spend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        currencyFormat.format(trip.totalSpend),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Transactions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${trip.transactionCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
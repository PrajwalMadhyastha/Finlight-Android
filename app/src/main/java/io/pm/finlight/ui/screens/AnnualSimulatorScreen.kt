package io.pm.finlight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.PrivacyAwareText
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.AnnualSimulatorViewModel

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualSimulatorScreen(
    navController: NavController,
    viewModel: AnnualSimulatorViewModel
) {
    val lifeEvents by viewModel.lifeEvents.collectAsState()
    val baseAnnualIncome by viewModel.baseAnnualIncome.collectAsState()
    val baseAnnualBudget by viewModel.baseAnnualBudget.collectAsState()
    val currentYear by viewModel.currentYear.collectAsState()
    val privacyModeEnabled by viewModel.privacyModeEnabled.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    val projectedImpact = viewModel.calculateProjectedAnnualImpact()
    val projectedDeficitSurplus = baseAnnualIncome - baseAnnualBudget - projectedImpact

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annual Simulator") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    io.pm.finlight.ui.components.HelpActionIcon(helpKey = "annual_simulator")
                    IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                        Icon(
                            imageVector = if (privacyModeEnabled) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle Privacy"
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .testTag("annual_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                // Hero Card for Annual Projection
                GlassPanel(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Projected Annual Balance ($currentYear)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Privacy mode handled by state
                        PrivacyAwareText(
                            amount = projectedDeficitSurplus,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (projectedDeficitSurplus >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            isPrivacyMode = privacyModeEnabled
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Projected Income",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                PrivacyAwareText(
                                    amount = baseAnnualIncome,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    isPrivacyMode = privacyModeEnabled
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Planned Budget",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                PrivacyAwareText(
                                    amount = baseAnnualBudget,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    isPrivacyMode = privacyModeEnabled
                                )
                            }
                        }
                        if (projectedImpact > 0) {
                            Row {
                                Text(
                                    "Event Impact: -",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                                PrivacyAwareText(
                                    amount = projectedImpact,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    isPrivacyMode = privacyModeEnabled
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Life Events",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { showAddDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Event",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            if (lifeEvents.isEmpty()) {
                item {
                    Text(
                        text = "No life events added. Add one to see its impact on your annual savings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                    )
                }
            } else {
                items(lifeEvents, key = { it.id }) { event ->
                    GlassPanel(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (event.isRecurring) "Recurring (From Month ${event.startMonthIndex + 1})" else "One-Time (Month ${event.startMonthIndex + 1})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(modifier = Modifier.padding(end = 16.dp)) {
                                Text(
                                    text = "-",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                                PrivacyAwareText(
                                    amount = event.amount,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    isPrivacyMode = privacyModeEnabled
                                )
                            }
                            IconButton(onClick = { viewModel.removeLifeEvent(event.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Event", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var amountStr by remember { mutableStateOf("") }
        var isRecurring by remember { mutableStateOf(false) }
        var startMonth by remember { mutableStateOf(0) }

        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        var expanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Life Event") },
            containerColor = popupContainerColor,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Event Name (e.g., Buy a Car)") },
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { str ->
                            if (str.all { it.isDigit() }) {
                                amountStr = str
                            }
                        },
                        label = { Text("Amount") },
                        leadingIcon = { Text("₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isRecurring,
                            onCheckedChange = { isRecurring = it }
                        )
                        Text("Is Recurring (Monthly)")
                    }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = months[startMonth],
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Start Month") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            months.forEachIndexed { index, month ->
                                DropdownMenuItem(
                                    text = { Text(month) },
                                    onClick = {
                                        startMonth = index
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull()
                        if (name.isNotBlank() && amount != null) {
                            viewModel.addLifeEvent(name, amount, isRecurring, startMonth)
                            showAddDialog = false
                        }
                    },
                    enabled = name.isNotBlank() && amountStr.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

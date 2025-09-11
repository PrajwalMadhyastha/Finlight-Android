// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/SearchScreen.kt
// REASON: FIX - The screen now accepts an `expandFilters` boolean. The
// LaunchedEffect that automatically expands the filter panel now only runs if
// this new flag is true. This prevents the panel from opening when navigating
// from the ReportsScreen pie chart.
// FEATURE - A new `SearchableDropdown` for filtering by tags has been added to
// the filter panel. This allows users to narrow down their search results based
// on the tags they have applied to transactions.
// FIX - The filter panel expansion and search bar focus are now one-time events
// per screen entry, preventing them from re-triggering annoyingly when
// navigating back from a detail screen.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.*
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.TransactionItem
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    searchViewModel: SearchViewModel,
    transactionViewModel: TransactionViewModel,
    focusSearch: Boolean,
    expandFilters: Boolean
) {
    val searchUiState by searchViewModel.uiState.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var filtersAlreadyExpanded by remember { mutableStateOf(false) }
    var focusAlreadyRequested by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    LaunchedEffect(searchUiState.selectedCategory, expandFilters) {
        if (searchUiState.selectedCategory != null && expandFilters && !filtersAlreadyExpanded) {
            showFilters = true
            filtersAlreadyExpanded = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar and Filter Section
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = searchUiState.keyword,
                onValueChange = { searchViewModel.onKeywordChange(it) },
                label = { Text("Keyword (description, notes)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            GlassPanel {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFilters = !showFilters }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filters",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Filters",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showFilters) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showFilters) "Collapse Filters" else "Expand Filters",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = showFilters,
                        enter = expandVertically(animationSpec = tween(200)),
                        exit = shrinkVertically(animationSpec = tween(200))
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            SearchableDropdown(
                                label = "Account",
                                options = searchUiState.accounts,
                                selectedOption = searchUiState.selectedAccount,
                                onOptionSelected = { searchViewModel.onAccountChange(it) },
                                getDisplayName = { it.name },
                            )
                            SearchableDropdown(
                                label = "Category",
                                options = searchUiState.categories,
                                selectedOption = searchUiState.selectedCategory,
                                onOptionSelected = { searchViewModel.onCategoryChange(it) },
                                getDisplayName = { it.name },
                            )
                            // --- NEW: Add Tag dropdown filter ---
                            SearchableDropdown(
                                label = "Tag",
                                options = searchUiState.tags,
                                selectedOption = searchUiState.selectedTag,
                                onOptionSelected = { searchViewModel.onTagChange(it) },
                                getDisplayName = { it.name },
                            )
                            SearchableDropdown(
                                label = "Transaction Type",
                                options = listOf("All", "Income", "Expense"),
                                selectedOption = searchUiState.transactionType.replaceFirstChar { it.uppercase() },
                                onOptionSelected = { searchViewModel.onTypeChange(it) },
                                getDisplayName = { it },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                DateTextField(
                                    label = "Start Date",
                                    date = searchUiState.startDate,
                                    formatter = dateFormatter,
                                    onClick = { showStartDatePicker = true },
                                    onClear = { searchViewModel.onDateChange(start = null) },
                                    modifier = Modifier.weight(1f),
                                )
                                DateTextField(
                                    label = "End Date",
                                    date = searchUiState.endDate,
                                    formatter = dateFormatter,
                                    onClick = { showEndDatePicker = true },
                                    onClear = { searchViewModel.onDateChange(end = null) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            OutlinedButton(
                                onClick = { searchViewModel.clearFilters() },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Clear All Filters") }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Search Results
        if (searchResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Results (${searchResults.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(searchResults) { transactionDetails ->
                    TransactionItem(
                        transactionDetails = transactionDetails,
                        onClick = { navController.navigate("transaction_detail/${transactionDetails.transaction.id}") },
                        onCategoryClick = { transactionViewModel.requestCategoryChange(it) }
                    )
                }
            }
        } else if (searchUiState.hasSearched) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No transactions match your criteria.")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (focusSearch && !focusAlreadyRequested) {
            focusRequester.requestFocus()
            focusAlreadyRequested = true
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = searchUiState.startDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    searchViewModel.onDateChange(start = datePickerState.selectedDateMillis)
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = searchUiState.endDate)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    searchViewModel.onDateChange(end = datePickerState.selectedDateMillis)
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableDropdown(
    label: String,
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T?) -> Unit,
    getDisplayName: (T) -> String,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedOption?.let { getDisplayName(it) } ?: "All",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Row {
                    if (selectedOption != null) {
                        IconButton(onClick = { onOptionSelected(null) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear selection")
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier =
                Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(
                if (isSystemInDarkTheme()) PopupSurfaceDark else PopupSurfaceLight
            )
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(getDisplayName(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun DateTextField(
    label: String,
    date: Long?,
    formatter: SimpleDateFormat,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = date?.let { formatter.format(Date(it)) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier.clickable(onClick = onClick),
        trailingIcon = {
            if (date != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, "Clear Date")
                }
            } else {
                Icon(Icons.Default.DateRange, "Select Date")
            }
        },
    )
}
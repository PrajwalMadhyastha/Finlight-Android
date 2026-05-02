// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/FilterBottomSheet.kt
// REASON: NEW FILE - A reusable composable for the filter bottom sheet to be
// used across the Transaction and Income screens, avoiding code duplication.
// FIX: Explicitly set the title's text color to ensure proper contrast in dark mode.
// FIX: Applied a semi-opaque background to the filter dropdown menus to ensure
// legibility and consistency with other popups in dark mode.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.TransactionFilterState
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight

@Composable
fun FilterBottomSheet(
    filterState: TransactionFilterState,
    accounts: List<Account>,
    categories: List<Category>,
    onKeywordChange: (String) -> Unit,
    onAccountChange: (Account?) -> Unit,
    onCategoryChange: (Category?) -> Unit,
    showTransactionTypeFilter: Boolean = true,
    onTransactionTypeChange: (io.pm.finlight.ui.viewmodel.AnalysisTransactionType) -> Unit = {},
    onClearFilters: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Filter Transactions",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = filterState.keyword,
            onValueChange = onKeywordChange,
            label = { Text("Search by keyword") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        if (showTransactionTypeFilter) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Transaction Type",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    io.pm.finlight.ui.viewmodel.AnalysisTransactionType.values().forEach { type ->
                        FilterChip(
                            selected = filterState.transactionType == type,
                            onClick = { onTransactionTypeChange(type) },
                            label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        }

        SearchableDropdown(
            label = "Account",
            options = accounts,
            selectedOption = filterState.account,
            onOptionSelected = onAccountChange,
            getDisplayName = { it.name },
        )

        SearchableDropdown(
            label = "Category",
            options = categories,
            selectedOption = filterState.category,
            onOptionSelected = onCategoryChange,
            getDisplayName = { it.name },
        )

        Button(
            onClick = onClearFilters,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear All Filters")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SearchableDropdown(
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
            modifier =
                Modifier.background(
                    if (isSystemInDarkTheme()) PopupSurfaceDark else PopupSurfaceLight,
                ),
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

package io.pm.finlight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.BudgetViewModel
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.utils.CategoryIconHelper
import io.pm.finlight.utils.FormatUtils

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualBudgetPlanningScreen(
    navController: NavController,
    viewModel: BudgetViewModel = viewModel(),
) {
    val selectedYear by viewModel.selectedPlanningYear.collectAsState()
    val overallSummary by viewModel.annualOverallSummary.collectAsState()
    val categorySummaries by viewModel.annualCategorySummaries.collectAsState()

    var showOverallDialog by remember { mutableStateOf(false) }
    var selectedCategoryForEdit by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.setSelectedPlanningYear(selectedYear)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annual Budget Plan") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    io.pm.finlight.ui.components.HelpActionIcon(helpKey = "annual_budget_planning")
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Selected Year", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.setSelectedPlanningYear(selectedYear - 1) }) {
                            Text("<")
                        }
                        Text(selectedYear.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { viewModel.setSelectedPlanningYear(selectedYear + 1) }) {
                            Text(">")
                        }
                    }
                }
            }

            item {
                GlassPanel(modifier = Modifier.clickable { showOverallDialog = true }) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                        Text("Overall Annual Budget", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        val amount = overallSummary?.totalBudget ?: 0f
                        Text(
                            FormatUtils.currencyFormatter.apply { maximumFractionDigits = 0 }.format(amount),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (overallSummary != null && overallSummary!!.overrideCount > 0) {
                            Text(
                                "Includes ${overallSummary!!.overrideCount} manual monthly overrides",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "No manual overrides set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Category Annual Budgets",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            items(categorySummaries, key = { it.categoryName }) { summary ->
                GlassPanel(modifier = Modifier.clickable { selectedCategoryForEdit = summary.categoryName }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(CategoryIconHelper.getIconBackgroundColor(summary.colorKey ?: "gray_light")),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = CategoryIconHelper.getIcon(summary.iconKey ?: "category"),
                                contentDescription = summary.categoryName,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(summary.categoryName, style = MaterialTheme.typography.titleMedium)
                            if (summary.overrideCount > 0) {
                                Text(
                                    "Includes ${summary.overrideCount} overrides",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            FormatUtils.currencyFormatter.apply { maximumFractionDigits = 0 }.format(summary.totalBudget),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showOverallDialog) {
        AnnualBudgetEditDialog(
            title = "Set Overall Annual Budget",
            currentBudget = overallSummary?.totalBudget?.toDouble() ?: 0.0,
            hasOverrides = (overallSummary?.overrideCount ?: 0) > 0,
            onDismiss = { showOverallDialog = false },
            onConfirm = { amount, isStrict ->
                viewModel.saveAnnualOverallBudget(amount, isStrict)
                showOverallDialog = false
            }
        )
    }

    selectedCategoryForEdit?.let { categoryName ->
        val summary = categorySummaries.find { it.categoryName == categoryName }
        AnnualBudgetEditDialog(
            title = "Set Annual Budget: $categoryName",
            currentBudget = summary?.totalBudget ?: 0.0,
            hasOverrides = (summary?.overrideCount ?: 0) > 0,
            onDismiss = { selectedCategoryForEdit = null },
            onConfirm = { amount, isStrict ->
                viewModel.saveAnnualCategoryBudget(categoryName, amount, isStrict)
                selectedCategoryForEdit = null
            }
        )
    }
}

@Composable
fun AnnualBudgetEditDialog(
    title: String,
    currentBudget: Double,
    hasOverrides: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit
) {
    var budgetInput by remember {
        val initial = if (currentBudget > 0.0) currentBudget.toLong().toString() else ""
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    var isStrict by remember { mutableStateOf(true) }

    val isThemeDark = MaterialTheme.colorScheme.background.isDark()
    val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { tfv ->
                        if (tfv.text.all { char -> char.isDigit() }) {
                            budgetInput = tfv
                        }
                    },
                    label = { Text("Total Annual Amount") },
                    leadingIcon = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasOverrides) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { isStrict = !isStrict },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = isStrict,
                            onCheckedChange = { isStrict = it }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Strict Target", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Adjusts remaining months so the final year total perfectly matches the target.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(budgetInput.text, isStrict) },
                enabled = budgetInput.text.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = popupContainerColor,
    )
}

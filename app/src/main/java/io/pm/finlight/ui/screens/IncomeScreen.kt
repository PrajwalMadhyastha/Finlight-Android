// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/IncomeScreen.kt
// REASON: FEATURE (Help System - Phase 2) - Integrated the HelpActionIcon into
// the TopAppBar to provide users with guidance on the screen's features, such
// as the different tabs and filtering options.
// FIX (UI) - Removed the local Scaffold and TopAppBar. The main NavHost now
// provides a centralized TopAppBar, and this change removes the duplicate,
// resolving a UI bug.
//
// REASON: MODIFIED - The screen now collects `isPrivacyModeEnabled` and passes
// it to the `IncomeHeader`. The header itself is updated to accept this
// flag and use `PrivacyAwareText` for displaying the total income.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.MonthlySummaryItem
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.components.FilterBottomSheet
import io.pm.finlight.ui.components.GlassPanel
import io.pm.finlight.ui.components.PrivacyAwareText
import io.pm.finlight.ui.components.TransactionList
import io.pm.finlight.ui.components.pagerTabIndicatorOffset
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.IncomeViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToLong

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(
    navController: NavController,
    incomeViewModel: IncomeViewModel = viewModel(),
    transactionViewModel: TransactionViewModel
) {
    val tabs = listOf("Credits", "Categories")
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    val incomeTransactions by incomeViewModel.incomeTransactionsForSelectedMonth.collectAsState()
    val incomeByCategory by incomeViewModel.incomeByCategoryForSelectedMonth.collectAsState()
    val totalIncome by incomeViewModel.totalIncomeForSelectedMonth.collectAsState()
    val selectedMonth by incomeViewModel.selectedMonth.collectAsState()
    val monthlySummaries by incomeViewModel.monthlySummaries.collectAsState()

    val filterState by incomeViewModel.filterState.collectAsState()
    val allAccounts by incomeViewModel.allAccounts.collectAsState()
    val allCategories by incomeViewModel.allCategories.collectAsState(initial = emptyList())
    var showFilterSheet by remember { mutableStateOf(false) }

    // --- NEW: Collect privacy mode state ---
    val isPrivacyModeEnabled by incomeViewModel.isPrivacyModeEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        IncomeHeader(
            totalIncome = totalIncome,
            selectedMonth = selectedMonth,
            monthlySummaries = monthlySummaries,
            onMonthSelected = { incomeViewModel.setSelectedMonth(it) },
            isPrivacyModeEnabled = isPrivacyModeEnabled // --- NEW: Pass state
        )

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.pagerTabIndicatorOffset(pagerState, tabPositions)
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(title) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> TransactionList(
                    transactions = incomeTransactions,
                    navController = navController,
                    onCategoryClick = { transactionViewModel.requestCategoryChange(it) }
                )
                1 -> CategorySpendingScreen(
                    spendingList = incomeByCategory,
                    onCategoryClick = { categorySpendingItem ->
                        val category = allCategories.find { it.name == categorySpendingItem.categoryName }
                        incomeViewModel.updateFilterCategory(category)
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                )
            }
        }
    }


    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            FilterBottomSheet(
                filterState = filterState,
                accounts = allAccounts,
                categories = allCategories,
                onKeywordChange = incomeViewModel::updateFilterKeyword,
                onAccountChange = incomeViewModel::updateFilterAccount,
                onCategoryChange = incomeViewModel::updateFilterCategory,
                onClearFilters = incomeViewModel::clearFilters
            )
        }
    }
}

@Composable
fun IncomeHeader(
    selectedMonth: Calendar,
    monthlySummaries: List<MonthlySummaryItem>,
    totalIncome: Long,
    onMonthSelected: (Calendar) -> Unit,
    // --- NEW: Accept privacy mode state ---
    isPrivacyModeEnabled: Boolean
) {
    val monthFormat = SimpleDateFormat("LLL", Locale.getDefault())
    val monthYearFormat = SimpleDateFormat("LLLL yyyy", Locale.getDefault())
    var showMonthScroller by remember { mutableStateOf(false) }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            .apply { maximumFractionDigits = 0 }
    }

    val selectedTabIndex = monthlySummaries.indexOfFirst {
        it.calendar.get(Calendar.MONTH) == selectedMonth.get(Calendar.MONTH) &&
                it.calendar.get(Calendar.YEAR) == selectedMonth.get(Calendar.YEAR)
    }.coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMonthScroller = !showMonthScroller }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = monthYearFormat.format(selectedMonth.time),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (showMonthScroller) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (showMonthScroller) "Hide month selector" else "Show month selector",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        AnimatedVisibility(
            visible = showMonthScroller,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                indicator = {},
                divider = {}
            ) {
                monthlySummaries.forEach { summaryItem ->
                    val isSelected = summaryItem.calendar.get(Calendar.MONTH) == selectedMonth.get(Calendar.MONTH) &&
                            summaryItem.calendar.get(Calendar.YEAR) == selectedMonth.get(Calendar.YEAR)
                    Tab(
                        selected = isSelected,
                        onClick = {
                            onMonthSelected(summaryItem.calendar)
                            showMonthScroller = false
                        },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = monthFormat.format(summaryItem.calendar.time),
                                    style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currencyFormat.format(summaryItem.totalSpent.roundToLong()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("Total Income", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // --- MODIFIED: Use PrivacyAwareText ---
                PrivacyAwareText(
                    amount = totalIncome,
                    isPrivacyMode = isPrivacyModeEnabled,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
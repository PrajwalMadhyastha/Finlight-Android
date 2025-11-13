// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/TransactionListScreen.kt
// REASON: FEATURE - The ModalBottomSheet for the snapshot customization screen
// now uses `WindowInsets(0)` to remove default padding. This allows the sheet's
// content to expand to be truly full-screen and edge-to-edge, improving the
// user experience.
// FIX - The sheet state is now configured with `skipPartiallyExpanded = true`
// to ensure it opens in a fully expanded state by default.
//
// REASON: MODIFIED - The screen now collects `isPrivacyModeEnabled` and passes
// it to the `MonthlySummaryHeader`. The header itself is updated to accept
// this flag and use `PrivacyAwareText` for displaying amounts.
//
// REASON: MODIFIED - Extended Privacy Mode to the `BudgetProgress` bar's
// "Spent" and "Budget" text fields.
// =================================================================================
package io.pm.finlight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pm.finlight.MonthlySummaryItem
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.components.FilterBottomSheet
import io.pm.finlight.ui.components.PrivacyAwareText
import io.pm.finlight.ui.components.ShareSnapshotSheet
import io.pm.finlight.ui.components.TransactionList
import io.pm.finlight.ui.components.pagerTabIndicatorOffset
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToLong

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    navController: NavController,
    viewModel: TransactionViewModel,
    initialTab: Int = 0
) {
    val tabs = listOf("Transactions", "Categories", "Merchants")
    val pagerState = rememberPagerState(initialPage = initialTab) { tabs.size }
    val scope = rememberCoroutineScope()

    val transactions by viewModel.transactionsForSelectedMonth.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val monthlySummaries by viewModel.monthlySummaries.collectAsState()
    val categorySpending by viewModel.categorySpendingForSelectedMonth.collectAsState()
    val merchantSpending by viewModel.merchantSpendingForSelectedMonth.collectAsState()
    val totalSpent by viewModel.monthlyExpenses.collectAsState()
    val totalIncome by viewModel.monthlyIncome.collectAsState()
    val budget by viewModel.overallMonthlyBudget.collectAsState()

    val filterState by viewModel.filterState.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState(initial = emptyList())
    val showFilterSheet by viewModel.showFilterSheet.collectAsState()

    val isSelectionMode by viewModel.isSelectionModeActive.collectAsState()
    val selectedIds by viewModel.selectedTransactionIds.collectAsState()

    val showShareSheet by viewModel.showShareSheet.collectAsState()
    val shareableFields by viewModel.shareableFields.collectAsState()

    // --- NEW: Collect privacy mode state ---
    val isPrivacyModeEnabled by viewModel.isPrivacyModeEnabled.collectAsState()

    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelectionMode()
        }
    }


    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        MonthlySummaryHeader(
            selectedMonth = selectedMonth,
            monthlySummaries = monthlySummaries,
            totalSpent = totalSpent.roundToLong(),
            totalIncome = totalIncome.roundToLong(),
            budget = budget,
            onMonthSelected = { viewModel.setSelectedMonth(it) },
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
                    transactions = transactions,
                    navController = navController,
                    onCategoryClick = { viewModel.requestCategoryChange(it) },
                    isSelectionMode = isSelectionMode,
                    selectedIds = selectedIds,
                    onEnterSelectionMode = { viewModel.enterSelectionMode(it) },
                    onToggleSelection = { viewModel.toggleTransactionSelection(it) }
                )
                1 -> CategorySpendingScreen(
                    spendingList = categorySpending,
                    onCategoryClick = { categorySpendingItem ->
                        val month = selectedMonth.get(Calendar.MONTH) + 1
                        val year = selectedMonth.get(Calendar.YEAR)
                        val encodedCategoryName = URLEncoder.encode(categorySpendingItem.categoryName, "UTF-8")
                        navController.navigate("category_detail/$encodedCategoryName/$month/$year")
                    }
                )
                2 -> MerchantSpendingScreen(
                    merchantList = merchantSpending,
                    onMerchantClick = { merchantSpendingSummary ->
                        val month = selectedMonth.get(Calendar.MONTH) + 1
                        val year = selectedMonth.get(Calendar.YEAR)
                        val encodedMerchantName = URLEncoder.encode(merchantSpendingSummary.merchantName, "UTF-8")
                        navController.navigate("merchant_detail/$encodedMerchantName/$month/$year")
                    }
                )
            }
        }
    }


    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onFilterSheetDismiss() },
            containerColor = if (isSystemInDarkTheme()) PopupSurfaceDark else PopupSurfaceLight
        ) {
            FilterBottomSheet(
                filterState = filterState,
                accounts = allAccounts,
                categories = allCategories,
                onKeywordChange = viewModel::updateFilterKeyword,
                onAccountChange = viewModel::updateFilterAccount,
                onCategoryChange = viewModel::updateFilterCategory,
                onClearFilters = viewModel::clearFilters
            )
        }
    }

    if (showShareSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.onShareSheetDismiss() },
            sheetState = sheetState,
            containerColor = if (isSystemInDarkTheme()) PopupSurfaceDark else PopupSurfaceLight,
            windowInsets = WindowInsets(0)
        ) {
            ShareSnapshotSheet(
                selectedFields = shareableFields,
                onFieldToggle = { viewModel.onShareableFieldToggled(it) },
                onGenerateClick = {
                    viewModel.generateAndShareSnapshot(context)
                },
                onCancelClick = { viewModel.onShareSheetDismiss() }
            )
        }
    }
}

@Composable
fun MonthlySummaryHeader(
    selectedMonth: Calendar,
    monthlySummaries: List<MonthlySummaryItem>,
    totalSpent: Long,
    totalIncome: Long,
    budget: Float,
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
            enter = expandVertically(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200))
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
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = formatAmountInLakhs(summaryItem.totalSpent.roundToLong()),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Total Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // --- MODIFIED: Use PrivacyAwareText ---
                PrivacyAwareText(
                    amount = totalSpent,
                    isPrivacyMode = isPrivacyModeEnabled,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Column(horizontalAlignment = Alignment.End) {
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

        Spacer(Modifier.height(12.dp))

        if (budget > 0) {
            BudgetProgress(
                spent = totalSpent,
                budget = budget.roundToLong(),
                modifier = Modifier.padding(horizontal = 16.dp),
                isPrivacyModeEnabled = isPrivacyModeEnabled // --- NEW: Pass state
            )
        } else {
            Text(
                text = "No budget set for this month.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

private fun formatAmountInLakhs(amount: Long): String {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        .apply { maximumFractionDigits = 0 }
    if (amount < 1000) return currencyFormat.format(amount)
    if (amount < 100000) return "${currencyFormat.format(amount / 1000)}K"
    return "${NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }.format(amount / 100000.0)}L"
}


@Composable
fun BudgetProgress(
    spent: Long,
    budget: Long,
    modifier: Modifier = Modifier,
    // --- NEW: Accept privacy mode state ---
    isPrivacyModeEnabled: Boolean = false
) {
    val progress = if (budget > 0) (spent.toFloat() / budget.toFloat()) else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(1000), label = "")

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            .apply { maximumFractionDigits = 0 }
    }

    val progressColor = when {
        progress > 1f -> MaterialTheme.colorScheme.error
        progress > 0.8f -> Color(0xFFFBC02D) // Amber
        else -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // --- MODIFIED: Use PrivacyAwareText ---
            PrivacyAwareText(
                amount = spent,
                isPrivacyMode = isPrivacyModeEnabled,
                prefix = "Spent: ",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            // --- MODIFIED: Use PrivacyAwareText ---
            PrivacyAwareText(
                amount = budget,
                isPrivacyMode = isPrivacyModeEnabled,
                prefix = "Budget: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
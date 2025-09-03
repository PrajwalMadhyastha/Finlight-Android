// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/screens/DashboardScreen.kt
// REASON: FEATURE - The "Last Month Summary" card is now conditionally rendered
// at the top of the dashboard. Its visibility is controlled by the new
// `showLastMonthSummaryCard` StateFlow from the ViewModel, ensuring it only appears
// on the first of the month and can be dismissed by the user.
// UX REFINEMENT - The "Last Month Summary" card is now positioned after the main
// Hero Budget card for a less intrusive user experience.
// =================================================================================
package io.pm.finlight.ui.screens

import android.app.Application
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.pm.finlight.*
import io.pm.finlight.ui.components.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    dashboardViewModel: DashboardViewModel,
    transactionViewModel: TransactionViewModel
) {
    val visibleCards by dashboardViewModel.visibleCards.collectAsState()
    val yearlyConsistencyData by dashboardViewModel.yearlyConsistencyData.collectAsState()
    val budgetHealthSummary by dashboardViewModel.budgetHealthSummary.collectAsState()

    // --- NEW: Collect state for the summary card ---
    val showLastMonthSummary by dashboardViewModel.showLastMonthSummaryCard.collectAsState()
    val lastMonthSummary by dashboardViewModel.lastMonthSummary.collectAsState()


    LaunchedEffect(Unit) {
        dashboardViewModel.refreshBudgetSummary()
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.testTag("dashboard_lazy_column")
    ) {
        // Always render the hero card first if it's visible.
        val heroCard = visibleCards.find { it == DashboardCardType.HERO_BUDGET }
        if (heroCard != null) {
            item(key = heroCard.name) {
                DashboardCard(
                    cardType = heroCard,
                    navController = navController,
                    dashboardViewModel = dashboardViewModel,
                    transactionViewModel = transactionViewModel,
                    yearlyConsistencyData = yearlyConsistencyData,
                    budgetHealthSummary = budgetHealthSummary
                )
            }
        }

        // Conditionally display the summary card right after the hero card.
        if (showLastMonthSummary && lastMonthSummary != null) {
            item(key = "last_month_summary_card") {
                LastMonthSummaryCard(
                    summary = lastMonthSummary!!,
                    navController = navController,
                    onDismiss = { dashboardViewModel.dismissLastMonthSummaryCard() }
                )
            }
        }

        // Render the rest of the cards, excluding the hero card which is already rendered.
        val otherCards = visibleCards.filter { it != DashboardCardType.HERO_BUDGET }
        items(otherCards, key = { it.name }) { cardType ->
            Box(modifier = Modifier.animateItemPlacement(animationSpec = spring())) {
                DashboardCard(
                    cardType = cardType,
                    navController = navController,
                    dashboardViewModel = dashboardViewModel,
                    transactionViewModel = transactionViewModel,
                    yearlyConsistencyData = yearlyConsistencyData,
                    budgetHealthSummary = budgetHealthSummary
                )
            }
        }
    }
}

@Composable
private fun DashboardCard(
    cardType: DashboardCardType,
    navController: NavController,
    dashboardViewModel: DashboardViewModel,
    transactionViewModel: TransactionViewModel,
    yearlyConsistencyData: List<CalendarDayStatus>,
    budgetHealthSummary: String
) {
    val monthlyIncome by dashboardViewModel.monthlyIncome.collectAsState()
    val monthlyExpenses by dashboardViewModel.monthlyExpenses.collectAsState()
    val overallBudget by dashboardViewModel.overallMonthlyBudget.collectAsState()
    val recentTransactions by dashboardViewModel.recentTransactions.collectAsState()
    val accountsSummary by dashboardViewModel.accountsSummary.collectAsState()
    val safeToSpendPerDay by dashboardViewModel.safeToSpendPerDay.collectAsState()
    val budgetStatus by dashboardViewModel.budgetStatus.collectAsState()
    val amountRemaining by dashboardViewModel.amountRemaining.collectAsState()
    val monthYear = dashboardViewModel.monthYear

    when (cardType) {
        DashboardCardType.HERO_BUDGET -> DashboardHeroCard(
            totalBudget = overallBudget,
            amountSpent = monthlyExpenses.toFloat(),
            amountRemaining = amountRemaining,
            income = monthlyIncome.toFloat(),
            safeToSpend = safeToSpendPerDay,
            navController = navController,
            monthYear = monthYear,
            budgetHealthSummary = budgetHealthSummary
        )
        DashboardCardType.QUICK_ACTIONS -> AuroraQuickActionsCard(navController = navController)
        DashboardCardType.RECENT_TRANSACTIONS -> AuroraRecentTransactionsCard(
            transactions = recentTransactions,
            navController = navController,
            onCategoryClick = { transactionViewModel.requestCategoryChange(it) }
        )
        DashboardCardType.ACCOUNTS_CAROUSEL -> AccountsCarouselCard(accounts = accountsSummary, navController = navController)
        DashboardCardType.BUDGET_WATCH -> BudgetWatchCard(
            budgetStatus = budgetStatus,
            navController = navController,
        )
        DashboardCardType.SPENDING_CONSISTENCY -> {
            GlassPanel {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Yearly Spending Consistency",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (yearlyConsistencyData.isEmpty()) {
                        CircularProgressIndicator()
                    } else {
                        ConsistencyCalendar(
                            data = yearlyConsistencyData,
                            onDayClick = { date ->
                                navController.navigate("search_screen?date=${date.time}&focusSearch=false")
                            }
                        )
                    }
                }
            }
        }
    }
}


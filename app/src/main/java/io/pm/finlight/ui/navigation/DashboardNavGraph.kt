package io.pm.finlight.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.pm.finlight.DashboardViewModel
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.BottomNavItem
import io.pm.finlight.ui.screens.CustomizeDashboardScreen
import io.pm.finlight.ui.screens.DashboardScreen
import io.pm.finlight.ui.viewmodel.AccountViewModel

fun NavGraphBuilder.dashboardNavGraph(navController: NavController) {
    composable(
        route = "customize_dashboard",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val dashboardViewModel: DashboardViewModel = resolveActivityViewModel()
        CustomizeDashboardScreen(navController = navController, viewModel = dashboardViewModel)
    }

    composable(BottomNavItem.Dashboard.route) {
        val dashboardViewModel: DashboardViewModel = resolveActivityViewModel()
        val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
        val accountViewModel: AccountViewModel = resolveActivityViewModel()
        DashboardScreen(
            navController = navController,
            dashboardViewModel = dashboardViewModel,
            transactionViewModel = transactionViewModel,
            accountViewModel = accountViewModel,
        )
    }
}

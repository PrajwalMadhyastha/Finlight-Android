package io.pm.finlight.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.pm.finlight.ui.screens.AddEditBudgetScreen
import io.pm.finlight.ui.screens.AnnualBudgetPlanningScreen
import io.pm.finlight.ui.screens.BudgetScreen
import io.pm.finlight.ui.screens.CategoryListScreen
import io.pm.finlight.ui.viewmodel.BudgetViewModel
import io.pm.finlight.ui.viewmodel.CategoryViewModel

fun NavGraphBuilder.budgetsNavGraph(navController: NavController) {
    composable(
        route = "budget_screen",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val budgetViewModel: BudgetViewModel = resolveActivityViewModel()
        BudgetScreen(navController, budgetViewModel)
    }

    composable(
        route = "annual_budget_planning",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val budgetViewModel: BudgetViewModel = resolveActivityViewModel()
        AnnualBudgetPlanningScreen(navController, budgetViewModel)
    }

    composable(
        route = "add_budget",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val budgetViewModel: BudgetViewModel = resolveActivityViewModel()
        AddEditBudgetScreen(navController, budgetViewModel, null)
    }

    composable(
        route = "edit_budget/{budgetId}",
        arguments = listOf(navArgument("budgetId") { type = NavType.IntType }),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val budgetViewModel: BudgetViewModel = resolveActivityViewModel()
        AddEditBudgetScreen(navController, budgetViewModel, backStackEntry.arguments?.getInt("budgetId"))
    }

    composable(
        route = "category_list",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val categoryViewModel: CategoryViewModel = resolveActivityViewModel()
        CategoryListScreen(navController, categoryViewModel)
    }
}

package io.pm.finlight.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.screens.AddEditGoalScreen
import io.pm.finlight.ui.screens.GoalDetailScreen
import io.pm.finlight.ui.screens.GoalScreen
import io.pm.finlight.ui.viewmodel.GoalViewModel

fun NavGraphBuilder.goalsNavGraph(navController: NavController) {
    composable(
        route = "goal_screen",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val goalViewModel: GoalViewModel = resolveActivityViewModel()
        GoalScreen(navController, goalViewModel)
    }

    composable(
        route = "add_edit_goal/{goalId}",
        arguments = listOf(navArgument("goalId") { type = NavType.StringType }),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val goalIdString = backStackEntry.arguments?.getString("goalId")
        val goalId = if (goalIdString == "new" || goalIdString == null) null else goalIdString.toIntOrNull()
        val goalViewModel: GoalViewModel = resolveActivityViewModel()
        val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
        AddEditGoalScreen(navController, goalId, goalViewModel, transactionViewModel)
    }

    composable(
        route = "goal_detail/{goalId}",
        arguments = listOf(navArgument("goalId") { type = NavType.IntType }),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val goalId = backStackEntry.arguments?.getInt("goalId") ?: 0
        val goalViewModel: GoalViewModel = resolveActivityViewModel()
        GoalDetailScreen(goalId, navController, goalViewModel)
    }
}

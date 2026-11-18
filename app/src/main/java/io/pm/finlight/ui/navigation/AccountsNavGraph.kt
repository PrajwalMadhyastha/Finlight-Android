package io.pm.finlight.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.pm.finlight.ui.screens.AccountDetailScreen
import io.pm.finlight.ui.screens.AccountListScreen
import io.pm.finlight.ui.screens.AddEditAccountScreen
import io.pm.finlight.ui.viewmodel.AccountViewModel

fun NavGraphBuilder.accountsNavGraph(navController: NavController) {
    composable(
        route = "account_list",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val accountViewModel: AccountViewModel = resolveActivityViewModel()
        AccountListScreen(navController, accountViewModel)
    }

    composable(
        route = "add_account",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val accountViewModel: AccountViewModel = resolveActivityViewModel()
        AddEditAccountScreen(navController, accountViewModel, null)
    }

    composable(
        route = "edit_account/{accountId}",
        arguments = listOf(navArgument("accountId") { type = NavType.IntType }),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val accountViewModel: AccountViewModel = resolveActivityViewModel()
        AddEditAccountScreen(navController, accountViewModel, backStackEntry.arguments!!.getInt("accountId"))
    }

    composable(
        route = "account_detail/{accountId}",
        arguments = listOf(navArgument("accountId") { type = NavType.IntType }),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val accountViewModel: AccountViewModel = resolveActivityViewModel()
        AccountDetailScreen(navController, accountViewModel, backStackEntry.arguments!!.getInt("accountId"))
    }
}

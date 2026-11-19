package io.pm.finlight.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.pm.finlight.ui.screens.ManageIgnoreRulesScreen
import io.pm.finlight.ui.screens.ManageMerchantRulesScreen
import io.pm.finlight.ui.screens.ManageParseRulesScreen
import io.pm.finlight.ui.screens.RuleCreationScreen
import io.pm.finlight.ui.screens.TagManagementScreen
import io.pm.finlight.ui.viewmodel.ManageIgnoreRulesViewModel
import io.pm.finlight.ui.viewmodel.ManageMerchantRulesViewModel
import io.pm.finlight.ui.viewmodel.ManageParseRulesViewModel
import io.pm.finlight.ui.viewmodel.TagViewModel
import java.net.URLDecoder

fun NavGraphBuilder.rulesNavGraph(navController: NavController) {
    composable(
        route = "manage_parse_rules",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val manageParseRulesViewModel: ManageParseRulesViewModel = resolveActivityViewModel()
        ManageParseRulesScreen(navController, manageParseRulesViewModel)
    }

    composable(
        route = "manage_ignore_rules",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val manageIgnoreRulesViewModel: ManageIgnoreRulesViewModel = resolveActivityViewModel()
        ManageIgnoreRulesScreen(navController = navController, viewModel = manageIgnoreRulesViewModel)
    }

    composable(
        route = "manage_merchant_rules",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val manageMerchantRulesViewModel: ManageMerchantRulesViewModel = resolveActivityViewModel()
        ManageMerchantRulesScreen(navController = navController, viewModel = manageMerchantRulesViewModel)
    }

    composable(
        route = "tag_management",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val tagViewModel: TagViewModel = resolveActivityViewModel()
        TagManagementScreen(navController = navController, viewModel = tagViewModel)
    }

    composable(
        route = "rule_creation_screen?potentialTransactionJson={potentialTransactionJson}&ruleId={ruleId}",
        arguments =
            listOf(
                navArgument("potentialTransactionJson") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("ruleId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val json = backStackEntry.arguments?.getString("potentialTransactionJson")
        val ruleId = backStackEntry.arguments?.getInt("ruleId")
        RuleCreationScreen(
            navController = navController,
            potentialTransactionJson = json?.let { URLDecoder.decode(it, "UTF-8") },
            ruleId = if (ruleId == -1) null else ruleId,
        )
    }
}

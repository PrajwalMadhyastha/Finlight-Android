package io.pm.finlight.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.pm.finlight.ui.screens.AnnualSimulatorScreen
import io.pm.finlight.ui.screens.WhatIfSimulatorScreen
import io.pm.finlight.ui.viewmodel.AnnualSimulatorViewModel
import io.pm.finlight.ui.viewmodel.WhatIfViewModel

fun NavGraphBuilder.simulatorsNavGraph(navController: NavController) {
    composable(
        route = "what_if_simulator",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val whatIfViewModel: WhatIfViewModel = resolveActivityViewModel()
        WhatIfSimulatorScreen(navController, whatIfViewModel)
    }

    composable(
        route = "annual_simulator",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val annualSimulatorViewModel: AnnualSimulatorViewModel = resolveActivityViewModel()
        AnnualSimulatorScreen(navController, annualSimulatorViewModel)
    }
}

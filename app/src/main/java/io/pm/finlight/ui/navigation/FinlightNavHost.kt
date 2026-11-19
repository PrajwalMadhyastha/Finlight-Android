package io.pm.finlight.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.pm.finlight.ui.screens.SplashScreen
import io.pm.finlight.ui.viewmodel.SettingsViewModel

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinlightNavHost(
    navController: NavHostController,
    startDestination: String = "splash_screen",
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable("splash_screen") {
            val settingsViewModel: SettingsViewModel = resolveActivityViewModel()
            SplashScreen(navController = navController, settingsViewModel = settingsViewModel)
        }
        dashboardNavGraph(navController)
        transactionsNavGraph(navController)
        reportsNavGraph(navController)
        settingsNavGraph(navController)
        budgetsNavGraph(navController)
        accountsNavGraph(navController)
        goalsNavGraph(navController)
        rulesNavGraph(navController)
        simulatorsNavGraph(navController)
    }
}

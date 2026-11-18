package io.pm.finlight.ui.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import io.pm.finlight.di.FinlightViewModelFactory
import io.pm.finlight.ui.BottomNavItem
import io.pm.finlight.ui.screens.CsvValidationScreen
import io.pm.finlight.ui.screens.CurrencyTravelScreen
import io.pm.finlight.ui.screens.EditProfileScreen
import io.pm.finlight.ui.screens.ProfileScreen
import io.pm.finlight.ui.screens.SmsDebugScreen
import io.pm.finlight.ui.screens.TripDetailScreen
import io.pm.finlight.ui.screens.settings.AppearanceSettingsScreen
import io.pm.finlight.ui.screens.settings.AutomationSettingsScreen
import io.pm.finlight.ui.screens.settings.DataSettingsScreen
import io.pm.finlight.ui.screens.settings.NotificationSettingsScreen
import io.pm.finlight.ui.screens.sms.ReviewSmsScreen
import io.pm.finlight.ui.viewmodel.CurrencyViewModel
import io.pm.finlight.ui.viewmodel.ProfileViewModel
import io.pm.finlight.ui.viewmodel.SettingsViewModel

fun NavGraphBuilder.settingsNavGraph(navController: NavController) {
    composable(
        route = BottomNavItem.Profile.route,
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val profileViewModel: ProfileViewModel = resolveActivityViewModel()
        ProfileScreen(
            navController = navController,
            profileViewModel = profileViewModel,
        )
    }

    composable(
        route = "edit_profile",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val profileViewModel: ProfileViewModel = resolveActivityViewModel()
        EditProfileScreen(navController, profileViewModel)
    }

    composable(
        route = "appearance_settings",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val settingsViewModel: SettingsViewModel = resolveActivityViewModel()
        AppearanceSettingsScreen(navController, settingsViewModel)
    }

    composable(
        route = "automation_settings",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val settingsViewModel: SettingsViewModel = resolveActivityViewModel()
        AutomationSettingsScreen(navController, settingsViewModel)
    }

    composable(
        route = "notification_settings",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val settingsViewModel: SettingsViewModel = resolveActivityViewModel()
        NotificationSettingsScreen(navController, settingsViewModel)
    }

    composable(
        route = "data_settings",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val settingsViewModel: SettingsViewModel = resolveActivityViewModel()
        DataSettingsScreen(navController, settingsViewModel)
    }

    composable(
        route = "currency_travel_settings?tripId={tripId}",
        arguments =
            listOf(
                navArgument("tripId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val context = LocalContext.current
        val tripId = backStackEntry.arguments?.getInt("tripId")
        val currencyViewModel: CurrencyViewModel = viewModel(factory = FinlightViewModelFactory.create(context))
        CurrencyTravelScreen(navController, if (tripId == -1) null else tripId, currencyViewModel)
    }

    composable(
        route = "trip_detail/{tripId}/{tagId}",
        arguments =
            listOf(
                navArgument("tripId") { type = NavType.IntType },
                navArgument("tagId") { type = NavType.IntType },
            ),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val tripId = backStackEntry.arguments?.getInt("tripId") ?: 0
        val tagId = backStackEntry.arguments?.getInt("tagId") ?: 0
        TripDetailScreen(
            navController = navController,
            tripId = tripId,
            tagId = tagId,
        )
    }

    composable(
        route = "csv_validation_screen",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val settingsViewModel: SettingsViewModel = resolveActivityViewModel()
        CsvValidationScreen(navController, settingsViewModel)
    }

    composable(
        route = "review_sms_screen",
        deepLinks = listOf(navDeepLink { uriPattern = "app://finlight.pm.io/review_sms" }),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val settingsViewModel: SettingsViewModel = resolveActivityViewModel()
        ReviewSmsScreen(navController, settingsViewModel)
    }

    composable(
        route = "sms_debug_screen",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        SmsDebugScreen(navController = navController)
    }
}

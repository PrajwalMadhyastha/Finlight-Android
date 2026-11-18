package io.pm.finlight.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.google.gson.Gson
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.di.FinlightViewModelFactory
import io.pm.finlight.ui.BottomNavItem
import io.pm.finlight.ui.screens.AddRecurringTransactionScreen
import io.pm.finlight.ui.screens.AddTransactionScreen
import io.pm.finlight.ui.screens.IncomeScreen
import io.pm.finlight.ui.screens.LinkRecurringTransactionScreen
import io.pm.finlight.ui.screens.LinkTransactionScreen
import io.pm.finlight.ui.screens.SearchScreen
import io.pm.finlight.ui.screens.SplitTransactionScreen
import io.pm.finlight.ui.screens.TransactionDetailScreen
import io.pm.finlight.ui.screens.TransactionListScreen
import io.pm.finlight.ui.screens.recurring.RecurringTransactionScreen
import io.pm.finlight.ui.screens.sms.ApproveTransactionScreen
import io.pm.finlight.ui.viewmodel.AccountViewModel
import io.pm.finlight.ui.viewmodel.GoalViewModel
import io.pm.finlight.ui.viewmodel.IncomeViewModel
import io.pm.finlight.ui.viewmodel.SearchViewModel
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import java.net.URLDecoder

fun NavGraphBuilder.transactionsNavGraph(navController: NavController) {
    composable(
        route = "transaction_list?initialTab={initialTab}",
        arguments =
            listOf(
                navArgument("initialTab") {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val initialTab = backStackEntry.arguments?.getInt("initialTab") ?: 0
        val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
        TransactionListScreen(
            navController = navController,
            viewModel = transactionViewModel,
            initialTab = initialTab,
        )
    }

    composable(
        route = "search_screen?categoryId={categoryId}&date={date}&safeToSpend={safeToSpend}&focusSearch={focusSearch}&expandFilters={expandFilters}&query={query}",
        arguments =
            listOf(
                navArgument("categoryId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("date") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("safeToSpend") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("focusSearch") {
                    type = NavType.BoolType
                    defaultValue = true
                },
                navArgument("expandFilters") {
                    type = NavType.BoolType
                    defaultValue = true
                },
                navArgument("query") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val categoryId = backStackEntry.arguments?.getInt("categoryId") ?: -1
        val date = backStackEntry.arguments?.getLong("date") ?: -1L
        val safeToSpend = backStackEntry.arguments?.getLong("safeToSpend") ?: -1L
        val focusSearch = backStackEntry.arguments?.getBoolean("focusSearch") ?: true
        val expandFilters = backStackEntry.arguments?.getBoolean("expandFilters") ?: true
        val query = backStackEntry.arguments?.getString("query")

        val context = LocalContext.current
        val factory =
            FinlightViewModelFactory.createSearchFactory(
                context,
                if (categoryId != -1) categoryId else null,
                if (date != -1L) date else null,
                query,
            )
        val searchViewModel: SearchViewModel = viewModel(factory = factory)
        val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
        SearchScreen(
            navController = navController,
            searchViewModel = searchViewModel,
            transactionViewModel = transactionViewModel,
            focusSearch = focusSearch,
            expandFilters = expandFilters,
            safeToSpend = if (safeToSpend != -1L) safeToSpend else null,
        )
    }

    composable(
        route = "income_screen",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val incomeViewModel: IncomeViewModel = resolveActivityViewModel()
        val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
        IncomeScreen(navController, incomeViewModel, transactionViewModel)
    }

    composable(
        route = "approve_transaction_screen?potentialTxnJson={potentialTxnJson}",
        arguments =
            listOf(
                navArgument("potentialTxnJson") { type = NavType.StringType },
            ),
        deepLinks =
            listOf(
                navDeepLink { uriPattern = "app://finlight.pm.io/approve_transaction_screen?potentialTxnJson={potentialTxnJson}" },
            ),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val json = backStackEntry.arguments?.getString("potentialTxnJson")
        val potentialTxn = Gson().fromJson(URLDecoder.decode(json, "UTF-8"), PotentialTransaction::class.java)
        val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
        val settingsViewModel: SettingsViewModel = resolveActivityViewModel()

        ApproveTransactionScreen(
            navController = navController,
            transactionViewModel = transactionViewModel,
            settingsViewModel = settingsViewModel,
            potentialTxn = potentialTxn,
        )
    }

    composable(
        route = "add_transaction?isCsvEdit={isCsvEdit}&csvLineNumber={csvLineNumber}&initialDataJson={initialDataJson}&transactionType={transactionType}",
        arguments =
            listOf(
                navArgument("isCsvEdit") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("csvLineNumber") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("initialDataJson") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("transactionType") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val arguments = requireNotNull(backStackEntry.arguments)
        val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
        val goalViewModel: GoalViewModel = resolveActivityViewModel()
        AddTransactionScreen(
            navController = navController,
            viewModel = transactionViewModel,
            goalViewModel = goalViewModel,
            isCsvEdit = arguments.getBoolean("isCsvEdit"),
            initialDataJson = arguments.getString("initialDataJson")?.let { URLDecoder.decode(it, "UTF-8") },
            initialTransactionType = arguments.getString("transactionType"),
        )
    }

    composable(
        route = "transaction_detail/{transactionId}",
        arguments = listOf(navArgument("transactionId") { type = NavType.IntType }),
        deepLinks = listOf(navDeepLink { uriPattern = "app://finlight.pm.io/transaction_detail/{transactionId}" }),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val transactionId = backStackEntry.arguments!!.getInt("transactionId")
        val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
        val accountViewModel: AccountViewModel = resolveActivityViewModel()
        val settingsViewModel: SettingsViewModel = resolveActivityViewModel()
        TransactionDetailScreen(
            navController = navController,
            transactionId = transactionId,
            viewModel = transactionViewModel,
            accountViewModel = accountViewModel,
            onSaveRenameRule = { original, new -> settingsViewModel.saveMerchantRenameRule(original, new) },
        )
    }

    composable(
        route = "split_transaction/{transactionId}",
        arguments = listOf(navArgument("transactionId") { type = NavType.IntType }),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val transactionId = backStackEntry.arguments!!.getInt("transactionId")
        val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
        SplitTransactionScreen(
            navController = navController,
            transactionId = transactionId,
            transactionViewModel = transactionViewModel,
        )
    }

    composable(
        route = "link_transaction_screen/{potentialTransactionJson}",
        arguments = listOf(navArgument("potentialTransactionJson") { type = NavType.StringType }),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val json = backStackEntry.arguments?.getString("potentialTransactionJson") ?: ""
        LinkTransactionScreen(navController = navController, potentialTransactionJson = json)
    }

    // Legacy recurring link route (superseded by confirm_pending_transaction, retained for deep-link compatibility)
    composable(
        route = "link_recurring_transaction/{potentialTransactionJson}",
        arguments = listOf(navArgument("potentialTransactionJson") { type = NavType.StringType }),
        deepLinks = listOf(navDeepLink { uriPattern = "app://finlight.pm.io/link_recurring/{potentialTransactionJson}" }),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val json = backStackEntry.arguments?.getString("potentialTransactionJson") ?: ""
        LinkRecurringTransactionScreen(navController = navController, potentialTransactionJson = json)
    }

    composable(
        route = "recurring_transactions",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        RecurringTransactionScreen(navController = navController)
    }

    composable(
        route = "add_recurring_transaction?ruleId={ruleId}&patternSignature={patternSignature}",
        arguments =
            listOf(
                navArgument("ruleId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("patternSignature") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val ruleIdStr = backStackEntry.arguments?.getString("ruleId")
        val ruleId = ruleIdStr?.toIntOrNull()
        AddRecurringTransactionScreen(navController = navController, ruleId = ruleId)
    }

    composable(
        route = "confirm_pending_transaction/{transactionId}/{ruleId}",
        arguments =
            listOf(
                navArgument("transactionId") { type = NavType.IntType },
                navArgument("ruleId") { type = NavType.IntType },
            ),
        deepLinks =
            listOf(
                navDeepLink { uriPattern = "app://finlight.pm.io/confirm_pending_transaction/{transactionId}/{ruleId}" },
                navDeepLink { uriPattern = "app://finlight.pm.io/confirm_pending/{transactionId}/{ruleId}" },
            ),
    ) {
        LaunchedEffect(Unit) {
            navController.navigate(BottomNavItem.Dashboard.route) {
                popUpTo(BottomNavItem.Dashboard.route) { inclusive = true }
            }
        }
    }
}

package io.pm.finlight.ui.navigation

import android.os.Build
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.ui.BottomNavItem
import io.pm.finlight.ui.screens.AnalysisDetailScreen
import io.pm.finlight.ui.screens.AnalysisScreen
import io.pm.finlight.ui.screens.BatchAnalysisScreen
import io.pm.finlight.ui.screens.DrilldownScreen
import io.pm.finlight.ui.screens.ReportsScreen
import io.pm.finlight.ui.screens.TimePeriodReportScreen
import io.pm.finlight.ui.viewmodel.AnalysisDimension
import io.pm.finlight.ui.viewmodel.DrilldownType
import io.pm.finlight.ui.viewmodel.ReportsViewModel
import java.net.URLDecoder

fun NavGraphBuilder.reportsNavGraph(navController: NavController) {
    composable(
        route = BottomNavItem.Reports.route,
        deepLinks = listOf(navDeepLink { uriPattern = "app://finlight.pm.io/reports" }),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        val reportsViewModel: ReportsViewModel = resolveActivityViewModel()
        ReportsScreen(navController, reportsViewModel)
    }

    composable(
        route = "analysis_screen",
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) {
        AnalysisScreen(navController = navController)
    }

    if (io.pm.finlight.BuildConfig.ENABLE_DEV_TOOLS) {
        composable("batch_analysis") {
            BatchAnalysisScreen(navController = navController)
        }
    }

    composable(
        route = "analysis_detail_screen/{dimension}/{dimensionId}/{startDate}/{endDate}?title={title}",
        arguments =
            listOf(
                navArgument("dimension") { type = NavType.EnumType(AnalysisDimension::class.java) },
                navArgument("dimensionId") { type = NavType.StringType },
                navArgument("startDate") { type = NavType.LongType },
                navArgument("endDate") { type = NavType.LongType },
                navArgument("title") { type = NavType.StringType },
            ),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val dimension =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                backStackEntry.arguments?.getSerializable("dimension", AnalysisDimension::class.java)
            } else {
                @Suppress("DEPRECATION")
                backStackEntry.arguments?.getSerializable("dimension") as? AnalysisDimension
            }
        val dimensionId = backStackEntry.arguments?.getString("dimensionId")
        val title = URLDecoder.decode(backStackEntry.arguments?.getString("title"), "UTF-8")
        val startDate = backStackEntry.arguments?.getLong("startDate")
        val endDate = backStackEntry.arguments?.getLong("endDate")

        if (dimension != null && dimensionId != null && startDate != null && endDate != null) {
            val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
            AnalysisDetailScreen(
                navController = navController,
                dimension = dimension,
                dimensionId = dimensionId,
                title = title,
                startDate = startDate,
                endDate = endDate,
                transactionViewModel = transactionViewModel,
            )
        }
    }

    composable(
        route = "time_period_report_screen/{timePeriod}?date={date}&showPreviousMonth={showPreviousMonth}",
        arguments =
            listOf(
                navArgument("timePeriod") { type = NavType.EnumType(TimePeriod::class.java) },
                navArgument("date") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("showPreviousMonth") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        deepLinks =
            listOf(
                navDeepLink {
                    uriPattern = "app://finlight.pm.io/report/{timePeriod}?date={date}&showPreviousMonth={showPreviousMonth}"
                },
            ),
        enterTransition = standardEnterTransition,
        exitTransition = standardExitTransition,
        popEnterTransition = standardPopEnterTransition,
        popExitTransition = standardPopExitTransition,
    ) { backStackEntry ->
        val timePeriod =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                backStackEntry.arguments?.getSerializable("timePeriod", TimePeriod::class.java)
            } else {
                @Suppress("DEPRECATION")
                backStackEntry.arguments?.getSerializable("timePeriod") as? TimePeriod
            }
        val date = backStackEntry.arguments?.getLong("date")
        val showPreviousMonth = backStackEntry.arguments?.getBoolean("showPreviousMonth") ?: false
        if (timePeriod != null) {
            val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
            TimePeriodReportScreen(
                navController = navController,
                timePeriod = timePeriod,
                transactionViewModel = transactionViewModel,
                initialDateMillis = date,
                showPreviousMonth = showPreviousMonth,
            )
        }
    }

    composable(
        route = "category_detail/{categoryName}/{month}/{year}",
        arguments =
            listOf(
                navArgument("categoryName") { type = NavType.StringType },
                navArgument("month") { type = NavType.IntType },
                navArgument("year") { type = NavType.IntType },
            ),
    ) { backStackEntry ->
        val categoryName = URLDecoder.decode(backStackEntry.arguments?.getString("categoryName"), "UTF-8")
        val month = backStackEntry.arguments?.getInt("month") ?: 0
        val year = backStackEntry.arguments?.getInt("year") ?: 0
        val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
        DrilldownScreen(
            navController = navController,
            drilldownType = DrilldownType.CATEGORY,
            entityName = categoryName,
            month = month,
            year = year,
            transactionViewModel = transactionViewModel,
        )
    }

    composable(
        route = "merchant_detail/{merchantName}/{month}/{year}",
        arguments =
            listOf(
                navArgument("merchantName") { type = NavType.StringType },
                navArgument("month") { type = NavType.IntType },
                navArgument("year") { type = NavType.IntType },
            ),
    ) { backStackEntry ->
        val merchantName = URLDecoder.decode(backStackEntry.arguments?.getString("merchantName"), "UTF-8")
        val month = backStackEntry.arguments?.getInt("month") ?: 0
        val year = backStackEntry.arguments?.getInt("year") ?: 0
        val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
        DrilldownScreen(
            navController = navController,
            drilldownType = DrilldownType.MERCHANT,
            entityName = merchantName,
            month = month,
            year = year,
            transactionViewModel = transactionViewModel,
        )
    }
}

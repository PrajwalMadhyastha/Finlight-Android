package io.pm.finlight.ui.navigation

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import io.pm.finlight.ui.BottomNavItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class NavGraphRouteIntegrityTest {
    private lateinit var context: Context
    private lateinit var navController: NavController
    private lateinit var navGraph: NavGraph

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        navController = NavController(context)
        navController.navigatorProvider.addNavigator(ComposeNavigator())
        navGraph =
            navController.createGraph(startDestination = "splash_screen") {
                composable("splash_screen") {}
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

    @Test
    fun testAll52RoutesAreRegistered() {
        val routes = mutableSetOf<String>()
        navGraph.forEach { destination ->
            destination.route?.let { routes.add(it) }
        }

        val expectedRoutes =
            setOf(
                "splash_screen",
                "customize_dashboard",
                BottomNavItem.Dashboard.route,
                "transaction_list?initialTab={initialTab}",
                "search_screen?categoryId={categoryId}&date={date}&safeToSpend={safeToSpend}&focusSearch={focusSearch}&expandFilters={expandFilters}&query={query}",
                "income_screen",
                "approve_transaction_screen?potentialTxnJson={potentialTxnJson}",
                "add_transaction?isCsvEdit={isCsvEdit}&csvLineNumber={csvLineNumber}&initialDataJson={initialDataJson}&transactionType={transactionType}",
                "transaction_detail/{transactionId}",
                "split_transaction/{transactionId}",
                "link_transaction_screen/{potentialTransactionJson}",
                "link_recurring_transaction/{potentialTransactionJson}",
                "recurring_transactions",
                "add_recurring_transaction?ruleId={ruleId}&patternSignature={patternSignature}",
                "confirm_pending_transaction/{transactionId}/{ruleId}",
                BottomNavItem.Reports.route,
                "analysis_screen",
                "batch_analysis",
                "analysis_detail_screen/{dimension}/{dimensionId}/{startDate}/{endDate}?title={title}",
                "time_period_report_screen/{timePeriod}?date={date}&showPreviousMonth={showPreviousMonth}",
                "category_detail/{categoryName}/{month}/{year}",
                "merchant_detail/{merchantName}/{month}/{year}",
                BottomNavItem.Profile.route,
                "edit_profile",
                "appearance_settings",
                "automation_settings",
                "notification_settings",
                "data_settings",
                "currency_travel_settings?tripId={tripId}",
                "trip_detail/{tripId}/{tagId}",
                "csv_validation_screen",
                "review_sms_screen",
                "sms_debug_screen",
                "budget_screen",
                "annual_budget_planning",
                "add_budget",
                "edit_budget/{budgetId}",
                "category_list",
                "account_list",
                "add_account",
                "edit_account/{accountId}",
                "account_detail/{accountId}",
                "goal_screen",
                "add_edit_goal/{goalId}",
                "goal_detail/{goalId}",
                "manage_parse_rules",
                "manage_ignore_rules",
                "manage_merchant_rules",
                "tag_management",
                "rule_creation_screen?potentialTransactionJson={potentialTransactionJson}&ruleId={ruleId}",
                "what_if_simulator",
                "annual_simulator",
            )

        assertEquals("Total registered routes must match exactly 52", 52, routes.size)
        expectedRoutes.forEach { expectedRoute ->
            assertTrue("Route $expectedRoute must be registered", routes.contains(expectedRoute))
        }
    }

    @Test
    fun testDeepLinksIntegrity() {
        val reportsDest = navGraph.findNode(BottomNavItem.Reports.route)
        assertNotNull(reportsDest)
        assertTrue(reportsDest!!.hasDeepLink(Uri.parse("app://finlight.pm.io/reports")))

        val reviewSmsDest = navGraph.findNode("review_sms_screen")
        assertNotNull(reviewSmsDest)
        assertTrue(reviewSmsDest!!.hasDeepLink(Uri.parse("app://finlight.pm.io/review_sms")))

        val txnDetailDest = navGraph.findNode("transaction_detail/{transactionId}")
        assertNotNull(txnDetailDest)
        assertTrue(txnDetailDest!!.hasDeepLink(Uri.parse("app://finlight.pm.io/transaction_detail/123")))

        val confirmPendingDest = navGraph.findNode("confirm_pending_transaction/{transactionId}/{ruleId}")
        assertNotNull(confirmPendingDest)
        assertTrue(confirmPendingDest!!.hasDeepLink(Uri.parse("app://finlight.pm.io/confirm_pending_transaction/1/2")))
        assertTrue(confirmPendingDest.hasDeepLink(Uri.parse("app://finlight.pm.io/confirm_pending/1/2")))
    }
}

package io.pm.finlight.ui

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import io.pm.finlight.MainActivity
import io.pm.finlight.R
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.health.DatabaseHealthChecker
import io.pm.finlight.diagnostics.CrashDiagnosticsManager
import io.pm.finlight.ui.components.AuroraAnimatedBackground
import io.pm.finlight.ui.components.DaybreakAnimatedBackground
import io.pm.finlight.ui.components.DiagnosticReportPreviewSheet
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.components.pickers.CategoryPickerBottomSheet
import io.pm.finlight.ui.navigation.FinlightNavHost
import io.pm.finlight.ui.navigation.resolveActivityViewModel
import io.pm.finlight.ui.theme.AppTheme
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.DashboardViewModel
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.ui.viewmodel.AccountViewModel
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import io.pm.finlight.utils.findAppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    shortcutAction: String? = null,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context.findAppCompatActivity()

    val dashboardViewModel: DashboardViewModel = resolveActivityViewModel()
    val transactionViewModel: TransactionViewModel = resolveActivityViewModel()
    val settingsViewModel: SettingsViewModel = resolveActivityViewModel()
    val accountViewModel: AccountViewModel = resolveActivityViewModel()

    val userName by dashboardViewModel.userName.collectAsState()
    val profilePictureUri by dashboardViewModel.profilePictureUri.collectAsState()
    val filterState by transactionViewModel.filterState.collectAsState()
    val selectedTheme by settingsViewModel.selectedTheme.collectAsState()
    val isPrivacyModeEnabled by settingsViewModel.privacyModeEnabled.collectAsState()

    val transactionForCategoryChange by transactionViewModel.transactionForCategoryChange.collectAsState()

    val isTransactionSelectionMode by transactionViewModel.isSelectionModeActive.collectAsState()
    val isAccountSelectionMode by accountViewModel.isSelectionModeActive.collectAsState()
    val selectedIdsCount by transactionViewModel.selectedTransactionIds.map { it.size }.collectAsState(initial = 0)
    val showDeleteConfirmation by transactionViewModel.showDeleteConfirmation.collectAsState()

    var showCrashRecoveryDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val hasCrash =
            withContext(Dispatchers.IO) {
                CrashDiagnosticsManager.hasRecordedCrash(context)
            }
        if (hasCrash) {
            showCrashRecoveryDialog = true
        }
    }
    var showDiagnosticPreviewSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val bottomNavItems =
        listOf(
            BottomNavItem.Dashboard,
            BottomNavItem.Transactions,
            BottomNavItem.Reports,
            BottomNavItem.Profile,
        )

    LaunchedEffect(shortcutAction) {
        when (shortcutAction) {
            MainActivity.ACTION_ADD_EXPENSE -> {
                navController.navigate("add_transaction?transactionType=expense")
            }
            MainActivity.ACTION_ADD_INCOME -> {
                navController.navigate("add_transaction?transactionType=income")
            }
            MainActivity.ACTION_SEARCH -> {
                navController.navigate("search_screen")
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val baseCurrentRoute = currentRoute?.split("?")?.firstOrNull()?.split("/")?.firstOrNull()

    val showBottomBar = bottomNavItems.any { it.route == baseCurrentRoute }

    val screensWithCustomTopBars =
        setOf(
            "splash_screen",
            "add_transaction",
            "transaction_detail",
            "split_transaction",
            "rule_creation_screen",
            "csv_validation_screen",
            "link_transaction_screen",
            "link_recurring_transaction",
            "add_edit_goal",
            "approve_transaction_screen",
            "annual_budget_planning",
            "what_if_simulator",
            "annual_simulator",
            "recurring_transactions",
            "add_recurring_transaction",
            "goal_detail",
        )

    val showMainTopBar = baseCurrentRoute !in screensWithCustomTopBars

    val currentTitle =
        when {
            baseCurrentRoute == BottomNavItem.Profile.route -> "Profile"
            showBottomBar -> "Hi, $userName!"
            else -> screenTitles[currentRoute] ?: screenTitles[baseCurrentRoute] ?: "Finance App"
        }

    val fabRoutes =
        setOf(
            "account_list",
        )
    val showFab = baseCurrentRoute in fabRoutes && !isAccountSelectionMode

    Box(modifier = modifier.fillMaxSize()) {
        when (selectedTheme) {
            AppTheme.AURORA -> {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                AuroraAnimatedBackground()
            }
            AppTheme.DAYBREAK -> {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                DaybreakAnimatedBackground()
            }
            else -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {}
            }
        }

        Scaffold(
            topBar = {
                if (isTransactionSelectionMode && baseCurrentRoute == BottomNavItem.Transactions.route) {
                    TopAppBar(
                        title = { Text("$selectedIdsCount Selected") },
                        navigationIcon = {
                            IconButton(onClick = { transactionViewModel.clearSelectionMode() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                            }
                        },
                        actions = {
                            val canManualMerge by transactionViewModel.canManualMerge.collectAsState()
                            if (canManualMerge) {
                                IconButton(onClick = { transactionViewModel.openReviewMergeSheet() }) {
                                    Icon(Icons.Default.Merge, contentDescription = "Merge Transactions")
                                }
                            }

                            val canLinkAsReimbursement by transactionViewModel.canLinkAsReimbursement.collectAsState()
                            if (canLinkAsReimbursement) {
                                IconButton(onClick = { transactionViewModel.linkReimbursementFromSelection() }) {
                                    Icon(Icons.Default.Payments, contentDescription = "Link Repayment")
                                }
                            }
                            IconButton(onClick = { transactionViewModel.onDeleteSelectionClick() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                            IconButton(onClick = { transactionViewModel.onShareClick() }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    )
                } else if (showMainTopBar) {
                    TopAppBar(
                        title = { Text(currentTitle) },
                        navigationIcon = {
                            if (!showBottomBar) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            if (showBottomBar && baseCurrentRoute != BottomNavItem.Profile.route) {
                                AsyncImage(
                                    model = profilePictureUri ?: R.mipmap.ic_launcher,
                                    contentDescription = "User Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .padding(end = 16.dp)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                navController.navigate(BottomNavItem.Profile.route) {
                                                    popUpTo(BottomNavItem.Dashboard.route) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                )
                            }
                            when (baseCurrentRoute) {
                                BottomNavItem.Dashboard.route -> {
                                    IconButton(onClick = { settingsViewModel.setPrivacyModeEnabled(!isPrivacyModeEnabled) }) {
                                        Icon(
                                            imageVector = if (isPrivacyModeEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Privacy Mode",
                                        )
                                    }
                                    IconButton(onClick = { navController.navigate("customize_dashboard") }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Customize Dashboard")
                                    }
                                    IconButton(onClick = { navController.navigate("search_screen") }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                }
                                BottomNavItem.Transactions.route -> {
                                    val areFiltersActive by remember(filterState) {
                                        derivedStateOf {
                                            filterState.keyword.isNotBlank() || filterState.account != null || filterState.category != null
                                        }
                                    }
                                    HelpActionIcon(helpKey = "transaction_list")
                                    IconButton(onClick = { navController.navigate("add_transaction") }) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                                    }
                                    BadgedBox(
                                        badge = {
                                            if (areFiltersActive) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary),
                                                )
                                            }
                                        },
                                    ) {
                                        IconButton(onClick = { transactionViewModel.onFilterClick() }) {
                                            Icon(Icons.Default.FilterList, contentDescription = "Filter Transactions")
                                        }
                                    }
                                }
                                "account_list" -> {
                                    if (!isAccountSelectionMode) {
                                        TextButton(onClick = { accountViewModel.enterSelectionMode(null) }) {
                                            Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = "Merge Accounts")
                                            Spacer(Modifier.width(4.dp))
                                            Text("Merge Accounts")
                                        }
                                    }
                                    HelpActionIcon(helpKey = "account_list")
                                }
                                BottomNavItem.Reports.route -> HelpActionIcon(helpKey = "reports_screen")
                                "goal_screen" -> {
                                    TextButton(onClick = { navController.navigate("add_edit_goal/new") }) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Goal")
                                        Spacer(Modifier.width(4.dp))
                                        Text("Add New Goal")
                                    }
                                }
                                "budget_screen" -> HelpActionIcon(helpKey = "budget_screen")
                                "income_screen" -> HelpActionIcon(helpKey = "income_screen")
                                "appearance_settings" -> HelpActionIcon(helpKey = "appearance_settings")
                                "automation_settings" -> HelpActionIcon(helpKey = "automation_settings")
                                "notification_settings" -> HelpActionIcon(helpKey = "notification_settings")
                                "data_settings" -> HelpActionIcon(helpKey = "data_settings")
                                "currency_travel_settings" -> HelpActionIcon(helpKey = "currency_travel_settings")
                                "customize_dashboard" -> HelpActionIcon(helpKey = "dashboard_customize")
                                "analysis_screen" -> HelpActionIcon(helpKey = "analysis_screen")
                                "category_list" -> HelpActionIcon(helpKey = "category_list")
                                "tag_management" -> HelpActionIcon(helpKey = "tag_management")
                                "manage_parse_rules" -> HelpActionIcon(helpKey = "manage_parse_rules")
                                "manage_ignore_rules" -> HelpActionIcon(helpKey = "manage_ignore_rules")
                                "manage_merchant_rules" -> HelpActionIcon(helpKey = "manage_merchant_rules")
                                "add_budget", "edit_budget" -> HelpActionIcon(helpKey = "add_budget")
                                "analysis_detail_screen" -> HelpActionIcon(helpKey = "analysis_detail_screen")
                                "trip_detail" -> HelpActionIcon(helpKey = "trip_detail")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                    ) {
                        bottomNavItems.forEach { screen ->
                            val isSelected = baseCurrentRoute == screen.route
                            NavigationBarItem(
                                modifier = Modifier.testTag("nav_item_${screen.label}"),
                                icon = { Icon(screen.icon, contentDescription = screen.label) },
                                label = { Text(screen.label) },
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(BottomNavItem.Dashboard.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(onClick = {
                        when (baseCurrentRoute) {
                            "account_list" -> navController.navigate("add_account")
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            FinlightNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
            )
        }

        if (transactionForCategoryChange != null) {
            val categories by transactionViewModel.allCategories.collectAsState(initial = emptyList())
            val isThemeDark = isSystemInDarkTheme()
            val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val onDismiss = { transactionViewModel.cancelCategoryChange() }

            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
                containerColor = popupContainerColor,
            ) {
                CategoryPickerBottomSheet(
                    title = "Change Category",
                    categories = categories,
                    selectedCategoryId = transactionForCategoryChange?.transaction?.categoryId,
                    onCategorySelected = { newCategory ->
                        transactionViewModel.updateTransactionCategory(transactionForCategoryChange!!.transaction.id, newCategory.id)
                        transactionViewModel.cancelCategoryChange()
                    },
                    onDismiss = onDismiss,
                    onAddNew = null,
                )
            }
        }

        if (showDeleteConfirmation) {
            val isThemeDark = MaterialTheme.colorScheme.background.isDark()
            val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

            AlertDialog(
                onDismissRequest = { transactionViewModel.onCancelDeleteSelection() },
                title = { Text("Delete Transactions?") },
                text = {
                    Text(
                        "Are you sure you want to permanently delete the selected $selectedIdsCount transaction(s)? This action cannot be undone.",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { transactionViewModel.onConfirmDeleteSelection() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { transactionViewModel.onCancelDeleteSelection() }) { Text("Cancel") }
                },
                containerColor = popupContainerColor,
            )
        }

        if (showCrashRecoveryDialog) {
            val isThemeDark = MaterialTheme.colorScheme.background.isDark()
            val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight
            var isRepairing by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = {
                    if (!isRepairing) {
                        CrashDiagnosticsManager.clearCrash(context)
                        showCrashRecoveryDialog = false
                    }
                },
                icon = {
                    Icon(
                        Icons.Default.Healing,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = { Text("App Recovered") },
                text = {
                    Text(
                        "Finlight recovered from an unexpected error. Your financial data is safe. You can view the privacy-redacted diagnostic report or run a quick database self-repair.",
                    )
                },
                confirmButton = {
                    Button(
                        enabled = !isRepairing,
                        onClick = {
                            if (isRepairing) return@Button
                            isRepairing = true
                            coroutineScope.launch {
                                try {
                                    val db = AppDatabase.getInstance(context)
                                    val result =
                                        withContext(Dispatchers.IO) {
                                            DatabaseHealthChecker.repair(db, context)
                                        }
                                    CrashDiagnosticsManager.clearCrash(context)
                                    showCrashRecoveryDialog = false
                                    val msg =
                                        if (result.success) {
                                            val totalFixed =
                                                result.repairedAccountsCount +
                                                    result.relinkedTransactionsCount +
                                                    result.repairedPreferencesCount
                                            if (totalFixed > 0) {
                                                "Database repaired: $totalFixed issues resolved."
                                            } else {
                                                "Database verified and healthy."
                                            }
                                        } else {
                                            "Database repair completed with warnings."
                                        }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                } finally {
                                    isRepairing = false
                                }
                            }
                        },
                    ) {
                        Text(if (isRepairing) "Repairing..." else "Self-Repair")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            enabled = !isRepairing,
                            onClick = {
                                showCrashRecoveryDialog = false
                                showDiagnosticPreviewSheet = true
                            },
                        ) {
                            Text("View Report")
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            enabled = !isRepairing,
                            onClick = {
                                CrashDiagnosticsManager.clearCrash(context)
                                showCrashRecoveryDialog = false
                            },
                        ) {
                            Text("Dismiss")
                        }
                    }
                },
                containerColor = popupContainerColor,
            )
        }

        if (showDiagnosticPreviewSheet) {
            DiagnosticReportPreviewSheet(
                onDismissRequest = {
                    CrashDiagnosticsManager.clearCrash(context)
                    showDiagnosticPreviewSheet = false
                },
            )
        }
    }
}

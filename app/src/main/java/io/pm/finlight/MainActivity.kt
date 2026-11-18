// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/MainActivity.kt
// REASON: FEATURE (UI Scaling) - Upgraded font scaling fix to handle Display Size.
// 1. Renamed `ForceSmallFonts` to `ForceAppScaling`.
// 2. Added logic to clamp screen density if width < 375dp (fixes system zoom issues).
// 3. Updated `setContent` to use the new `ForceAppScaling` wrapper.
// =================================================================================
package io.pm.finlight

import android.annotation.SuppressLint
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import io.pm.finlight.ui.navigation.FinlightNavHost
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import io.pm.finlight.data.DataExportService
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.FinlightViewModelFactory
import io.pm.finlight.data.health.DatabaseHealthChecker
import io.pm.finlight.diagnostics.CrashDiagnosticsManager
import io.pm.finlight.ui.BottomNavItem
import io.pm.finlight.ui.common.ForceAppScaling
import io.pm.finlight.ui.common.WindowSecurityController
import io.pm.finlight.ui.components.AuroraAnimatedBackground
import io.pm.finlight.ui.components.DaybreakAnimatedBackground
import io.pm.finlight.ui.components.DiagnosticReportPreviewSheet
import io.pm.finlight.ui.components.HelpActionIcon
import io.pm.finlight.ui.components.pickers.CategoryPickerBottomSheet
import io.pm.finlight.ui.screenTitles
import io.pm.finlight.ui.screens.*
import io.pm.finlight.ui.theme.AppTheme
import io.pm.finlight.ui.theme.PersonalFinanceAppTheme
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.ui.viewmodel.*
// REMOVED: import io.pm.finlight.ui.common.ForceSmallFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

class MainActivity : AppCompatActivity() {
    companion object {
        const val ACTION_ADD_EXPENSE = "io.pm.finlight.ACTION_ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "io.pm.finlight.ACTION_ADD_INCOME"
        const val ACTION_SEARCH = "io.pm.finlight.ACTION_SEARCH"
        private val MIN_SNAPSHOT_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)
        private var lastSnapshotTimestamp = 0L
    }

    /**
     * Public wrapper around the protected [onNewIntent] for use in instrumented tests.
     * This allows tests to simulate an incoming deep link intent while the activity
     * is already running (i.e., the same way a notification PendingIntent works).
     */
    @androidx.annotation.VisibleForTesting
    fun handleIntentForTesting(intent: android.content.Intent) {
        onNewIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val application = LocalContext.current.applicationContext as Application
            val transactionViewModel: TransactionViewModel = viewModel(factory = FinlightViewModelFactory.create(application))
            val settingsViewModel: SettingsViewModel = viewModel(factory = FinlightViewModelFactory.create(application))
            val selectedTheme by settingsViewModel.selectedTheme.collectAsState()
            val hasSeenOnboarding by settingsViewModel.hasSeenOnboarding.collectAsState()
            val isPrivacyModeEnabled by settingsViewModel.privacyModeEnabled.collectAsState()

            LaunchedEffect(isPrivacyModeEnabled) {
                WindowSecurityController.updateSecureFlag(window, isPrivacyModeEnabled)
            }

            // UPDATED: Using ForceAppScaling to handle both Text and Display scaling
            ForceAppScaling {
                PersonalFinanceAppTheme(selectedTheme = selectedTheme) {
                    if (hasSeenOnboarding == false) {
                        val onboardingViewModel: OnboardingViewModel = viewModel(factory = FinlightViewModelFactory.create(application))
                        OnboardingScreen(
                            viewModel = onboardingViewModel,
                            onOnboardingFinished = {
                                settingsViewModel.setHasSeenOnboarding(true)
                            },
                        )
                    } else if (hasSeenOnboarding == true) {
                        FinanceAppWithLockScreen(
                            shortcutAction = intent?.action,
                            settingsViewModel = settingsViewModel,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NotificationManagerCompat.from(this).cancelAll()
    }

    override fun onStop() {
        super.onStop()
        val now = System.currentTimeMillis()
        if (now - lastSnapshotTimestamp >= MIN_SNAPSHOT_INTERVAL_MS) {
            lastSnapshotTimestamp = now
            lifecycleScope.launch {
                DataExportService.createBackupSnapshot(applicationContext)
            }
        }
    }
}

@SuppressLint("NewApi")
@Composable
fun FinanceAppWithLockScreen(
    shortcutAction: String? = null,
    settingsViewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val appLockEnabled by settingsViewModel.appLockEnabled.collectAsState()
    var isUnlocked by remember { mutableStateOf(false) }

    val permissionsToRequest =
        remember {
            val list =
                mutableListOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            list.toTypedArray()
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { perms ->
            val allPermissionsGranted = perms.all { it.value }
            if (!allPermissionsGranted) {
                Toast.makeText(context, "Some permissions were denied. The app may not function fully.", Toast.LENGTH_LONG).show()
            }
        }

    LaunchedEffect(key1 = true) {
        val areAllPermissionsGranted =
            permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        if (!areAllPermissionsGranted) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    val biometricManager = remember { BiometricManager.from(context) }
    val canAuthenticate =
        remember {
            val authenticators =
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        }

    if (appLockEnabled == null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {}
    } else if (appLockEnabled == true && canAuthenticate && !isUnlocked) {
        LockScreen(onUnlock = { isUnlocked = true })
    } else {
        MainAppScreen(shortcutAction = shortcutAction)
    }
}

@SuppressWarnings("kotlin:S5324")
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalContext.current as FragmentActivity
    val executor: Executor = remember { ContextCompat.getMainExecutor(context) }

    val promptInfo =
        remember {
            val authenticators =
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("App Locked")
                .setSubtitle("Unlock using biometrics, PIN, pattern, or password")
                .setAllowedAuthenticators(authenticators)
                .build()
        }

    val biometricPrompt =
        remember {
            BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onUnlock()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            Toast.makeText(context, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

    LaunchedEffect(Unit) {
        biometricPrompt.authenticate(promptInfo)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Button(onClick = { biometricPrompt.authenticate(promptInfo) }) {
            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Unlock App")
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(shortcutAction: String? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext as Application

    val dashboardViewModel: DashboardViewModel = viewModel(factory = FinlightViewModelFactory.create(context))
    val transactionViewModel: TransactionViewModel = viewModel(factory = FinlightViewModelFactory.create(context))
    val settingsViewModel: SettingsViewModel = viewModel(factory = FinlightViewModelFactory.create(context))
    val accountViewModel: AccountViewModel = viewModel(factory = FinlightViewModelFactory.create(context))

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

    // Handle shortcut navigation
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

    val activity = LocalContext.current as AppCompatActivity

    Box(modifier = Modifier.fillMaxSize()) {
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

                            // --- NEW: Link Repayment action (visible only when 1 expense + 1+ incomes selected) ---
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
                            if (!showBottomBar) { // Show back arrow on non-bottom-nav screens
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

@Composable
fun SplashScreen(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
) {
    var statusText by remember { mutableStateOf("Initializing...") }
    val context = LocalContext.current

    LaunchedEffect(key1 = true) {
        val isFirstLaunch = !settingsViewModel.isFirstLaunchComplete.first()
        val snapshotFile = DataExportService.getSnapshotFile(context)
        val shouldCheckRestore =
            isFirstLaunch ||
                withContext(Dispatchers.IO) {
                    snapshotFile.exists() &&
                        AppDatabase.getInstance(context).transactionQueryDao().getAllTransactionsSimple().first().isEmpty()
                }

        if (shouldCheckRestore) {
            statusText = "Checking for restored data..."
            val restored =
                withContext(Dispatchers.IO) {
                    DataExportService.restoreFromBackupSnapshot(context)
                }
            if (restored) {
                statusText = "Data restored successfully!"
                delay(1500) // Give user time to see the message
            }
            // Set the flag *after* the restore check is complete
            settingsViewModel.setFirstLaunchComplete()
        }

        statusText = "Loading dashboard..."
        delay(200) // A small delay for a smoother transition
        navController.navigate(BottomNavItem.Dashboard.route) {
            popUpTo("splash_screen") { inclusive = true }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(statusText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

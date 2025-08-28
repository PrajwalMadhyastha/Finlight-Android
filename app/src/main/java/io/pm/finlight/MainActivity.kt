// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/MainActivity.kt
// REASON: FEATURE - The NavHost entry for the `sms_debug_screen` has been updated
// to pass the `transactionViewModel`. This is required so the debug screen's
// ViewModel can save transactions after a new rule is created.
// =================================================================================
package io.pm.finlight

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import coil.compose.AsyncImage
import com.google.gson.Gson
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.ui.BottomNavItem
import io.pm.finlight.ui.components.AuroraAnimatedBackground
import io.pm.finlight.ui.components.DaybreakAnimatedBackground
import io.pm.finlight.ui.screenTitles
import io.pm.finlight.ui.screens.*
import io.pm.finlight.ui.theme.AppTheme
import io.pm.finlight.ui.theme.PersonalFinanceAppTheme
import io.pm.finlight.ui.theme.PopupSurfaceDark
import io.pm.finlight.ui.theme.PopupSurfaceLight
import io.pm.finlight.utils.CategoryIconHelper
import kotlinx.coroutines.flow.map
import java.net.URLDecoder
import java.util.concurrent.Executor

private fun Color.isDark() = (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )


        val settingsRepository = SettingsRepository(this)
        val hasSeenOnboarding = settingsRepository.hasSeenOnboarding()

        setContent {
            val transactionViewModel: TransactionViewModel = viewModel()
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(application, transactionViewModel)
            )
            val selectedTheme by settingsViewModel.selectedTheme.collectAsState()

            PersonalFinanceAppTheme(selectedTheme = selectedTheme) {
                var showOnboarding by remember { mutableStateOf(!hasSeenOnboarding) }

                if (showOnboarding) {
                    val onboardingViewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModelFactory(application))
                    OnboardingScreen(
                        viewModel = onboardingViewModel,
                        onOnboardingFinished = {
                            settingsRepository.setHasSeenOnboarding(true)
                            showOnboarding = false
                        }
                    )
                } else {
                    FinanceAppWithLockScreen(isInitiallyLocked = settingsRepository.isAppLockEnabledBlocking())
                }
            }
        }
    }
}

@Composable
fun FinanceAppWithLockScreen(isInitiallyLocked: Boolean) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }

    var isLocked by remember { mutableStateOf(isInitiallyLocked) }
    val appLockEnabled by settingsRepository.getAppLockEnabled().collectAsState(initial = isInitiallyLocked)

    val permissionsToRequest = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.POST_NOTIFICATIONS
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allPermissionsGranted = perms.all { it.value }
        if (!allPermissionsGranted) {
            Toast.makeText(context, "Some permissions were denied. The app may not function fully.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(key1 = true) {
        val areAllPermissionsGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!areAllPermissionsGranted) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    LaunchedEffect(appLockEnabled) {
        if (!appLockEnabled) {
            isLocked = false
        }
    }

    if (isLocked) {
        LockScreen(onUnlock = { isLocked = false })
    } else {
        MainAppScreen()
    }
}


@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalContext.current as FragmentActivity
    val executor: Executor = remember { ContextCompat.getMainExecutor(context) }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("App Locked")
            .setSubtitle("Authenticate to access your finances")
            .setNegativeButtonText("Cancel")
            .build()
    }

    val biometricPrompt = remember {
        BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onUnlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        Toast.makeText(context, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })
    }

    LaunchedEffect(Unit) {
        biometricPrompt.authenticate(promptInfo)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = { biometricPrompt.authenticate(promptInfo) }) {
            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Unlock App")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext as Application

    val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModelFactory(context))
    val transactionViewModel: TransactionViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context, transactionViewModel))
    val accountViewModel: AccountViewModel = viewModel()
    val categoryViewModel: CategoryViewModel = viewModel()
    val budgetViewModel: BudgetViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val incomeViewModel: IncomeViewModel = viewModel()
    val goalViewModel: GoalViewModel = viewModel()

    val userName by dashboardViewModel.userName.collectAsState()
    val profilePictureUri by dashboardViewModel.profilePictureUri.collectAsState()
    val filterState by transactionViewModel.filterState.collectAsState()
    val selectedTheme by settingsViewModel.selectedTheme.collectAsState()

    val transactionForCategoryChange by transactionViewModel.transactionForCategoryChange.collectAsState()

    val isSelectionMode by transactionViewModel.isSelectionModeActive.collectAsState()
    val selectedIdsCount by transactionViewModel.selectedTransactionIds.map { it.size }.collectAsState(initial = 0)
    val showDeleteConfirmation by transactionViewModel.showDeleteConfirmation.collectAsState()

    val bottomNavItems = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Transactions,
        BottomNavItem.Reports
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val baseCurrentRoute = currentRoute?.split("?")?.firstOrNull()?.split("/")?.firstOrNull()

    val showBottomBar = bottomNavItems.any { it.route == baseCurrentRoute }

    val showMainTopBar = baseCurrentRoute !in setOf(
        "transaction_detail",
        "income_screen",
        "splash_screen",
        "add_transaction",
        "time_period_report_screen",
        "link_recurring_transaction",
        "appearance_settings",
        "automation_settings",
        "notification_settings",
        "data_settings",
        "add_edit_goal",
        "currency_travel_settings",
        "split_transaction",
        "category_detail",
        "merchant_detail",
        "customize_dashboard",
        "account_mapping_screen",
        "sms_debug_screen"
    )

    val currentTitle = if (showBottomBar) {
        "Hi, $userName!"
    } else {
        screenTitles[currentRoute] ?: screenTitles[baseCurrentRoute] ?: "Finance App"
    }
    val showProfileIcon = showBottomBar

    val fabRoutes = setOf(
        "account_list",
        "recurring_transactions",
        "goals_screen"
    )
    val showFab = baseCurrentRoute in fabRoutes

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
                    color = MaterialTheme.colorScheme.background
                ) {}
            }
        }

        Scaffold(
            topBar = {
                if (isSelectionMode && baseCurrentRoute == BottomNavItem.Transactions.route) {
                    TopAppBar(
                        title = { Text("$selectedIdsCount Selected") },
                        navigationIcon = {
                            IconButton(onClick = { transactionViewModel.clearSelectionMode() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = { transactionViewModel.onDeleteSelectionClick() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                            IconButton(onClick = { transactionViewModel.onShareClick() }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                } else if (showMainTopBar) {
                    TopAppBar(
                        title = { Text(currentTitle) },
                        navigationIcon = {
                            if (showProfileIcon) {
                                AsyncImage(
                                    model = profilePictureUri ?: R.mipmap.ic_launcher,
                                    contentDescription = "User Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .padding(start = 16.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { navController.navigate("profile") }
                                )
                            } else if (!showBottomBar) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            when (baseCurrentRoute) {
                                BottomNavItem.Dashboard.route -> {
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
                                    IconButton(onClick = { navController.navigate("add_transaction") }) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                                    }
                                    BadgedBox(
                                        badge = {
                                            if (areFiltersActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    ) {
                                        IconButton(onClick = { transactionViewModel.onFilterClick() }) {
                                            Icon(Icons.Default.FilterList, contentDescription = "Filter Transactions")
                                        }
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = Color.Transparent
                    ) {
                        bottomNavItems.forEach { screen ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
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
                                }
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
                            "recurring_transactions" -> navController.navigate("add_recurring_transaction")
                            "goals_screen" -> navController.navigate("add_edit_goal")
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
                activity = activity,
                dashboardViewModel = dashboardViewModel,
                settingsViewModel = settingsViewModel,
                transactionViewModel = transactionViewModel,
                accountViewModel = accountViewModel,
                categoryViewModel = categoryViewModel,
                budgetViewModel = budgetViewModel,
                profileViewModel = profileViewModel,
                incomeViewModel = incomeViewModel,
                goalViewModel = goalViewModel
            )
        }

        if (transactionForCategoryChange != null) {
            val categories by transactionViewModel.allCategories.collectAsState(initial = emptyList())
            val isThemeDark = isSystemInDarkTheme()
            val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = { transactionViewModel.cancelCategoryChange() },
                sheetState = sheetState,
                containerColor = popupContainerColor
            ) {
                CategoryPickerSheet(
                    title = "Change Category",
                    items = categories,
                    onItemSelected = { newCategory ->
                        transactionViewModel.updateTransactionCategory(transactionForCategoryChange!!.transaction.id, newCategory.id)
                        transactionViewModel.cancelCategoryChange()
                    },
                    onAddNew = null
                )
            }
        }

        if (showDeleteConfirmation) {
            val isThemeDark = MaterialTheme.colorScheme.surface.isDark()
            val popupContainerColor = if (isThemeDark) PopupSurfaceDark else PopupSurfaceLight

            AlertDialog(
                onDismissRequest = { transactionViewModel.onCancelDeleteSelection() },
                title = { Text("Delete Transactions?") },
                text = { Text("Are you sure you want to permanently delete the selected $selectedIdsCount transaction(s)? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = { transactionViewModel.onConfirmDeleteSelection() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { transactionViewModel.onCancelDeleteSelection() }) { Text("Cancel") }
                },
                containerColor = popupContainerColor
            )
        }
    }
}


@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    activity: AppCompatActivity,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    transactionViewModel: TransactionViewModel,
    accountViewModel: AccountViewModel,
    categoryViewModel: CategoryViewModel,
    budgetViewModel: BudgetViewModel,
    profileViewModel: ProfileViewModel,
    incomeViewModel: IncomeViewModel,
    goalViewModel: GoalViewModel
) {
    NavHost(
        navController = navController,
        startDestination = "splash_screen",
        modifier = modifier
    ) {
        composable(
            "sms_debug_screen",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            SmsDebugScreen(
                navController = navController,
                transactionViewModel = transactionViewModel
            )
        }
        composable("account_mapping_screen") {
            AccountMappingScreen(
                navController = navController,
                settingsViewModel = settingsViewModel,
                accountViewModel = accountViewModel
            )
        }
        composable(
            "customize_dashboard",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            CustomizeDashboardScreen(navController = navController, viewModel = dashboardViewModel)
        }

        composable("splash_screen") {
            SplashScreen(navController = navController, activity = activity)
        }

        composable(
            "split_transaction/{transactionId}",
            arguments = listOf(navArgument("transactionId") { type = NavType.IntType }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments!!.getInt("transactionId")
            SplitTransactionScreen(navController = navController, transactionId = transactionId)
        }

        composable(
            "manage_parse_rules",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            ManageParseRulesScreen(navController)
        }
        composable(
            "manage_ignore_rules",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            ManageIgnoreRulesScreen()
        }

        composable(BottomNavItem.Dashboard.route) {
            DashboardScreen(
                navController = navController,
                dashboardViewModel = dashboardViewModel,
                transactionViewModel = transactionViewModel
            )
        }
        composable(
            route = "transaction_list?initialTab={initialTab}",
            arguments = listOf(navArgument("initialTab") {
                type = NavType.IntType
                defaultValue = 0
            }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getInt("initialTab") ?: 0
            TransactionListScreen(
                navController = navController,
                viewModel = transactionViewModel,
                initialTab = initialTab
            )
        }
        composable(
            route = BottomNavItem.Reports.route,
            deepLinks = listOf(navDeepLink { uriPattern = "app://finlight.pm.io/reports" }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { ReportsScreen(navController, viewModel()) }

        composable(
            "profile",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            ProfileScreen(
                navController = navController,
                profileViewModel = profileViewModel
            )
        }
        composable(
            "edit_profile",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { EditProfileScreen(navController, profileViewModel) }
        composable(
            "csv_validation_screen",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { CsvValidationScreen(navController, settingsViewModel) }
        composable(
            route = "search_screen?categoryId={categoryId}&date={date}&focusSearch={focusSearch}&expandFilters={expandFilters}",
            arguments = listOf(
                navArgument("categoryId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("date") { type = NavType.LongType; defaultValue = -1L },
                navArgument("focusSearch") { type = NavType.BoolType; defaultValue = true },
                navArgument("expandFilters") { type = NavType.BoolType; defaultValue = true }
            ),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getInt("categoryId") ?: -1
            val date = backStackEntry.arguments?.getLong("date") ?: -1L
            val focusSearch = backStackEntry.arguments?.getBoolean("focusSearch") ?: true
            val expandFilters = backStackEntry.arguments?.getBoolean("expandFilters") ?: true

            val factory = SearchViewModelFactory(
                activity.application,
                if (categoryId != -1) categoryId else null,
                if (date != -1L) date else null
            )
            val searchViewModel: SearchViewModel = viewModel(factory = factory)
            SearchScreen(navController, searchViewModel, transactionViewModel, focusSearch, expandFilters)
        }
        composable(
            route = "review_sms_screen",
            deepLinks = listOf(navDeepLink { uriPattern = "app://finlight.pm.io/review_sms" }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { ReviewSmsScreen(navController, settingsViewModel) }

        composable(
            "income_screen",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            IncomeScreen(navController, incomeViewModel, transactionViewModel)
        }

        composable(
            route = "approve_transaction_screen?potentialTxnJson={potentialTxnJson}",
            arguments = listOf(
                navArgument("potentialTxnJson") { type = NavType.StringType }
            ),
            deepLinks = listOf(navDeepLink { uriPattern = "app://finlight.pm.io/approve_transaction_screen?potentialTxnJson={potentialTxnJson}" }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val json = backStackEntry.arguments?.getString("potentialTxnJson")
            val potentialTxn = Gson().fromJson(URLDecoder.decode(json, "UTF-8"), PotentialTransaction::class.java)

            ApproveTransactionScreen(
                navController = navController,
                transactionViewModel = transactionViewModel,
                settingsViewModel = settingsViewModel,
                potentialTxn = potentialTxn
            )
        }

        composable(
            "add_transaction?isCsvEdit={isCsvEdit}&csvLineNumber={csvLineNumber}&initialDataJson={initialDataJson}",
            arguments = listOf(
                navArgument("isCsvEdit") { type = NavType.BoolType; defaultValue = false },
                navArgument("csvLineNumber") { type = NavType.IntType; defaultValue = -1 },
                navArgument("initialDataJson") { type = NavType.StringType; nullable = true; defaultValue = null }
            ),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val arguments = requireNotNull(backStackEntry.arguments)
            AddTransactionScreen(
                navController = navController,
                viewModel = transactionViewModel,
                isCsvEdit = arguments.getBoolean("isCsvEdit"),
                initialDataJson = arguments.getString("initialDataJson")?.let { URLDecoder.decode(it, "UTF-8") }
            )
        }

        composable(
            route = "transaction_detail/{transactionId}",
            arguments = listOf(navArgument("transactionId") { type = NavType.IntType }),
            deepLinks = listOf(navDeepLink { uriPattern = "app://finlight.pm.io/transaction_detail/{transactionId}" }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments!!.getInt("transactionId")
            TransactionDetailScreen(
                navController = navController,
                transactionId = transactionId,
                viewModel = transactionViewModel,
                accountViewModel = accountViewModel,
                onSaveRenameRule = { original, new -> settingsViewModel.saveMerchantRenameRule(original, new) }
            )
        }

        composable(
            "account_list",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { AccountListScreen(navController, accountViewModel) }

        composable(
            "add_account",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            AddEditAccountScreen(navController, accountViewModel, null)
        }
        composable(
            "edit_account/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.IntType }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            AddEditAccountScreen(navController, accountViewModel, backStackEntry.arguments!!.getInt("accountId"))
        }

        composable(
            "account_detail/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.IntType }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            AccountDetailScreen(navController, accountViewModel, backStackEntry.arguments!!.getInt("accountId"))
        }
        composable(
            "budget_screen",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { BudgetScreen(navController, budgetViewModel) }
        composable(
            "add_budget",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { AddEditBudgetScreen(navController, budgetViewModel, null) }
        composable(
            "edit_budget/{budgetId}",
            arguments = listOf(navArgument("budgetId") { type = NavType.IntType }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            AddEditBudgetScreen(navController, budgetViewModel, backStackEntry.arguments?.getInt("budgetId"))
        }
        composable(
            "category_list",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { CategoryListScreen(navController, categoryViewModel) }
        composable(
            "tag_management",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { TagManagementScreen() }
        composable(
            "recurring_transactions",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { RecurringTransactionScreen(navController) }

        composable(
            "add_recurring_transaction?ruleId={ruleId}",
            arguments = listOf(navArgument("ruleId") { type = NavType.IntType; defaultValue = -1 }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getInt("ruleId")
            AddRecurringTransactionScreen(navController = navController, ruleId = if (ruleId == -1) null else ruleId)
        }

        composable(
            "rule_creation_screen?potentialTransactionJson={potentialTransactionJson}&ruleId={ruleId}",
            arguments = listOf(
                navArgument("potentialTransactionJson") { type = NavType.StringType; nullable = true },
                navArgument("ruleId") { type = NavType.IntType; defaultValue = -1 }
            ),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val json = backStackEntry.arguments?.getString("potentialTransactionJson")
            val ruleId = backStackEntry.arguments?.getInt("ruleId")
            RuleCreationScreen(
                navController = navController,
                potentialTransactionJson = json?.let { URLDecoder.decode(it, "UTF-8") },
                ruleId = if (ruleId == -1) null else ruleId
            )
        }

        composable(
            "link_transaction_screen/{potentialTransactionJson}",
            arguments = listOf(navArgument("potentialTransactionJson") { type = NavType.StringType }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val json = backStackEntry.arguments?.getString("potentialTransactionJson") ?: ""
            LinkTransactionScreen(navController = navController, potentialTransactionJson = json)
        }

        composable(
            route = "link_recurring_transaction/{potentialTransactionJson}",
            arguments = listOf(navArgument("potentialTransactionJson") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "app://finlight.pm.io/link_recurring/{potentialTransactionJson}" }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val json = backStackEntry.arguments?.getString("potentialTransactionJson") ?: ""
            LinkRecurringTransactionScreen(navController = navController, potentialTransactionJson = json)
        }

        composable(
            "time_period_report_screen/{timePeriod}?date={date}&showPreviousMonth={showPreviousMonth}",
            arguments = listOf(
                navArgument("timePeriod") { type = NavType.EnumType(TimePeriod::class.java) },
                navArgument("date") { type = NavType.LongType; defaultValue = -1L },
                navArgument("showPreviousMonth") { type = NavType.BoolType; defaultValue = false }
            ),
            deepLinks = listOf(navDeepLink { uriPattern = "app://finlight.pm.io/report/{timePeriod}?date={date}&showPreviousMonth={showPreviousMonth}" }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val timePeriod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                backStackEntry.arguments?.getSerializable("timePeriod", TimePeriod::class.java)
            } else {
                @Suppress("DEPRECATION")
                backStackEntry.arguments?.getSerializable("timePeriod") as? TimePeriod
            }
            val date = backStackEntry.arguments?.getLong("date")
            val showPreviousMonth = backStackEntry.arguments?.getBoolean("showPreviousMonth") ?: false
            if (timePeriod != null) {
                TimePeriodReportScreen(
                    navController = navController,
                    timePeriod = timePeriod,
                    transactionViewModel = transactionViewModel,
                    initialDateMillis = date,
                    showPreviousMonth = showPreviousMonth
                )
            }
        }

        composable(
            "goals_screen",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            GoalScreen(navController = navController, goalViewModel = goalViewModel)
        }

        composable(
            "add_edit_goal",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            AddEditGoalScreen(navController = navController, goalId = null)
        }
        composable(
            "add_edit_goal/{goalId}",
            arguments = listOf(navArgument("goalId") { type = NavType.IntType }),
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val goalId = backStackEntry.arguments?.getInt("goalId")
            AddEditGoalScreen(navController = navController, goalId = goalId)
        }


        composable(
            "appearance_settings",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            AppearanceSettingsScreen(navController, settingsViewModel)
        }
        composable(
            "automation_settings",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            AutomationSettingsScreen(navController, settingsViewModel)
        }
        composable(
            "notification_settings",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            NotificationSettingsScreen(navController, settingsViewModel)
        }
        composable(
            "data_settings",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            DataSettingsScreen(navController, settingsViewModel)
        }
        composable(
            "currency_travel_settings",
            enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            CurrencyTravelScreen(navController)
        }
        composable(
            "category_detail/{categoryName}/{month}/{year}",
            arguments = listOf(
                navArgument("categoryName") { type = NavType.StringType },
                navArgument("month") { type = NavType.IntType },
                navArgument("year") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val categoryName = URLDecoder.decode(backStackEntry.arguments?.getString("categoryName"), "UTF-8")
            val month = backStackEntry.arguments?.getInt("month") ?: 0
            val year = backStackEntry.arguments?.getInt("year") ?: 0
            DrilldownScreen(
                navController = navController,
                drilldownType = DrilldownType.CATEGORY,
                entityName = categoryName,
                month = month,
                year = year
            )
        }
        composable(
            "merchant_detail/{merchantName}/{month}/{year}",
            arguments = listOf(
                navArgument("merchantName") { type = NavType.StringType },
                navArgument("month") { type = NavType.IntType },
                navArgument("year") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val merchantName = URLDecoder.decode(backStackEntry.arguments?.getString("merchantName"), "UTF-8")
            val month = backStackEntry.arguments?.getInt("month") ?: 0
            val year = backStackEntry.arguments?.getInt("year") ?: 0
            DrilldownScreen(
                navController = navController,
                drilldownType = DrilldownType.MERCHANT,
                entityName = merchantName,
                month = month,
                year = year
            )
        }
    }
}

@Composable
fun SplashScreen(navController: NavHostController, activity: Activity) {
    LaunchedEffect(key1 = Unit) {
        navController.navigate(BottomNavItem.Dashboard.route) {
            popUpTo("splash_screen") { inclusive = true }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CategoryPickerSheet(
    title: String,
    items: List<Category>,
    onItemSelected: (Category) -> Unit,
    onAddNew: (() -> Unit)? = null
) {
    Column(modifier = Modifier.navigationBarsPadding().fillMaxHeight()) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { category ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onItemSelected(category)
                        }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryIconDisplay(category)
                    Text(
                        category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (onAddNew != null) {
                item {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onAddNew)
                            .padding(vertical = 12.dp)
                            .height(80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.AddCircleOutline,
                            contentDescription = "Create New",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "New",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CategoryIconDisplay(category: Category) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(CategoryIconHelper.getIconBackgroundColor(category.colorKey)),
        contentAlignment = Alignment.Center
    ) {
        if (category.name == "Uncategorized") {
            Icon(
                imageVector = CategoryIconHelper.getIcon("help_outline"),
                contentDescription = category.name,
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        } else if (category.iconKey == "letter_default") {
            Text(
                text = category.name.firstOrNull()?.uppercase() ?: "?",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        } else {
            Icon(
                imageVector = CategoryIconHelper.getIcon(category.iconKey),
                contentDescription = category.name,
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

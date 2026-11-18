package io.pm.finlight.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.pm.finlight.data.DataExportService
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ui.BottomNavItem
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
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
                delay(1500)
            }
            settingsViewModel.setFirstLaunchComplete()
        }

        statusText = "Loading dashboard..."
        delay(200)
        navController.navigate(BottomNavItem.Dashboard.route) {
            popUpTo("splash_screen") { inclusive = true }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(statusText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

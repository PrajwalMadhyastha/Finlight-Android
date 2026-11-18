package io.pm.finlight

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.pm.finlight.data.DataExportService
import io.pm.finlight.di.FinlightViewModelFactory
import io.pm.finlight.ui.MainAppScaffold
import io.pm.finlight.ui.auth.FinanceAppWithLockScreen
import io.pm.finlight.ui.common.ForceAppScaling
import io.pm.finlight.ui.common.WindowSecurityController
import io.pm.finlight.ui.screens.OnboardingScreen
import io.pm.finlight.ui.theme.PersonalFinanceAppTheme
import io.pm.finlight.ui.viewmodel.OnboardingViewModel
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        const val ACTION_ADD_EXPENSE = "io.pm.finlight.ACTION_ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "io.pm.finlight.ACTION_ADD_INCOME"
        const val ACTION_SEARCH = "io.pm.finlight.ACTION_SEARCH"
        private val MIN_SNAPSHOT_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)
        private var lastSnapshotTimestamp = 0L
    }

    private var shortcutAction by mutableStateOf<String?>(null)

    /**
     * Public wrapper around the protected [onNewIntent] for use in instrumented tests.
     * This allows tests to simulate an incoming deep link intent while the activity
     * is already running (i.e., the same way a notification PendingIntent works).
     */
    @androidx.annotation.VisibleForTesting
    fun handleIntentForTesting(intent: Intent) {
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutAction = intent.action
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shortcutAction = intent?.action

        enableEdgeToEdge()

        setContent {
            val application = LocalContext.current.applicationContext as Application
            val settingsViewModel: SettingsViewModel = viewModel(factory = FinlightViewModelFactory.create(application))
            val selectedTheme by settingsViewModel.selectedTheme.collectAsState()
            val hasSeenOnboarding by settingsViewModel.hasSeenOnboarding.collectAsState()
            val isPrivacyModeEnabled by settingsViewModel.privacyModeEnabled.collectAsState()

            LaunchedEffect(isPrivacyModeEnabled) {
                WindowSecurityController.updateSecureFlag(window, isPrivacyModeEnabled)
            }

            ForceAppScaling {
                PersonalFinanceAppTheme(selectedTheme = selectedTheme) {
                    if (hasSeenOnboarding == false) {
                        val onboardingViewModel: OnboardingViewModel =
                            viewModel(factory = FinlightViewModelFactory.create(application))
                        OnboardingScreen(
                            viewModel = onboardingViewModel,
                            onOnboardingFinished = {
                                settingsViewModel.setHasSeenOnboarding(true)
                            },
                        )
                    } else if (hasSeenOnboarding == true) {
                        FinanceAppWithLockScreen(
                            shortcutAction = shortcutAction,
                            settingsViewModel = settingsViewModel,
                        ) {
                            MainAppScaffold(shortcutAction = shortcutAction)
                        }
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

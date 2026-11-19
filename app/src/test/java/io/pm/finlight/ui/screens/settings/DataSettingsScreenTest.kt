package io.pm.finlight.ui.screens.settings

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.TestApplication
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DataSettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dataSettingsScreen_rendersCoreSecurityAndExportOptions() {
        val navController = mockk<NavController>(relaxed = true)
        val settingsViewModel = mockk<SettingsViewModel>(relaxed = true)

        every { settingsViewModel.appLockEnabled } returns MutableStateFlow(false)
        every { settingsViewModel.healthCheckResult } returns MutableStateFlow(null)
        every { settingsViewModel.isCheckingHealth } returns MutableStateFlow(false)
        every { settingsViewModel.isRepairingDb } returns MutableStateFlow(false)
        every { settingsViewModel.repairResultMessage } returns MutableStateFlow(null)
        every { settingsViewModel.autoBackupEnabled } returns MutableStateFlow(false)
        every { settingsViewModel.privacyModeEnabled } returns MutableStateFlow(false)
        every { settingsViewModel.lastBackupTimestamp } returns MutableStateFlow(0L)
        every { settingsViewModel.showBackupSuccessDialog } returns MutableStateFlow(false)
        every { settingsViewModel.uiEvent } returns MutableSharedFlow()

        composeTestRule.setContent {
            DataSettingsScreen(
                navController = navController,
                settingsViewModel = settingsViewModel,
            )
        }

        composeTestRule.onNodeWithText("Enable App Lock").assertExists()
        composeTestRule.onNodeWithText("Automatic Daily Backup").assertExists()
        composeTestRule.onNodeWithText("Export Data (JSON)").assertExists()
    }
}

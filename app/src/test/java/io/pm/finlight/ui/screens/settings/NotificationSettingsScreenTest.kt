package io.pm.finlight.ui.screens.settings

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pm.finlight.TestApplication
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class NotificationSettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun notificationSettingsScreen_rendersSectionsAndTogglesNotification() {
        val navController = mockk<NavController>(relaxed = true)
        val settingsViewModel = mockk<SettingsViewModel>(relaxed = true)

        every { settingsViewModel.weeklySummaryEnabled } returns MutableStateFlow(true)
        every { settingsViewModel.dailyReportEnabled } returns MutableStateFlow(true)
        every { settingsViewModel.monthlySummaryEnabled } returns MutableStateFlow(true)
        every { settingsViewModel.dailyReportTime } returns MutableStateFlow(Pair(21, 0))
        every { settingsViewModel.weeklyReportTime } returns MutableStateFlow(Triple(1, 10, 0))
        every { settingsViewModel.autoCaptureNotificationEnabled } returns MutableStateFlow(true)
        every { settingsViewModel.autoBackupEnabled } returns MutableStateFlow(true)
        every { settingsViewModel.autoBackupNotificationEnabled } returns MutableStateFlow(false)

        composeTestRule.setContent {
            NotificationSettingsScreen(
                navController = navController,
                settingsViewModel = settingsViewModel,
            )
        }

        composeTestRule.onNodeWithText("Auto-Captured Transactions").assertExists().performClick()
        composeTestRule.onNodeWithText("Daily Summary").assertExists()
        composeTestRule.onNodeWithText("Weekly Summary").assertExists()

        verify { settingsViewModel.setAutoCaptureNotificationEnabled(false) }
    }
}

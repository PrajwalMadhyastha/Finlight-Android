package io.pm.finlight.ui.screens.settings

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AutomationSettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun automationSettingsScreen_rendersItemsAndTriggersScan() {
        val navController = mockk<NavController>(relaxed = true)
        val settingsViewModel = mockk<SettingsViewModel>(relaxed = true)

        every { settingsViewModel.isScanning } returns MutableStateFlow(false)
        every { settingsViewModel.smsScanStartDate } returns MutableStateFlow(1700000000000L)
        every { settingsViewModel.unknownTransactionPopupEnabled } returns MutableStateFlow(true)
        every { settingsViewModel.totalSmsToScan } returns MutableStateFlow(0)
        every { settingsViewModel.processedSmsCount } returns MutableStateFlow(0)
        every { settingsViewModel.uiEvent } returns MutableSharedFlow()

        composeTestRule.setContent {
            AutomationSettingsScreen(
                navController = navController,
                settingsViewModel = settingsViewModel,
            )
        }

        composeTestRule.onNodeWithText("Scan Full Inbox").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Manage Custom Parse Rules").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manage Parser Ignore List").assertIsDisplayed()

        verify { settingsViewModel.startSmsScanAndIdentifyMappings(null, any()) }
    }
}

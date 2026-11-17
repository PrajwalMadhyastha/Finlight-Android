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
import io.pm.finlight.ui.theme.AppTheme
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AppearanceSettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appearanceSettingsScreen_rendersThemesAndHandlesSelection() {
        val navController = mockk<NavController>(relaxed = true)
        val settingsViewModel = mockk<SettingsViewModel>(relaxed = true)

        every { settingsViewModel.selectedTheme } returns MutableStateFlow(AppTheme.AURORA)

        composeTestRule.setContent {
            AppearanceSettingsScreen(
                navController = navController,
                settingsViewModel = settingsViewModel,
            )
        }

        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select the app's color palette.").assertIsDisplayed()
        composeTestRule.onNodeWithText(AppTheme.DAYBREAK.displayName).assertIsDisplayed().performClick()

        verify { settingsViewModel.saveSelectedTheme(AppTheme.DAYBREAK) }
    }
}

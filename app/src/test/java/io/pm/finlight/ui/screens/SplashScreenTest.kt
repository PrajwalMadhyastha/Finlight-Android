package io.pm.finlight.ui.screens

import android.os.Build
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.TestApplication
import io.pm.finlight.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SplashScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashScreen_rendersInitialProgressAndStatus() {
        val navController = mockk<NavHostController>(relaxed = true)
        val settingsViewModel = mockk<SettingsViewModel>(relaxed = true)
        every { settingsViewModel.isFirstLaunchComplete } returns MutableStateFlow(true)

        composeTestRule.setContent {
            SplashScreen(
                navController = navController,
                settingsViewModel = settingsViewModel,
            )
        }

        composeTestRule.waitForIdle()
        // Either initial or loading dashboard text is displayed
        val node = composeTestRule.onNodeWithText("Loading dashboard...")
        node.assertExists()
    }
}

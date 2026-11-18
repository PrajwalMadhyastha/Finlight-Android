package io.pm.finlight.ui.auth

import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class LockScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun lockScreen_rendersUnlockButtonAndSafelyHandlesNoFragmentActivity() {
        var unlocked = false
        composeTestRule.setContent {
            LockScreen(
                onUnlock = { unlocked = true },
            )
        }

        val unlockButton = composeTestRule.onNodeWithText("Unlock App")
        unlockButton.assertExists()
        // In Robolectric without a host FragmentActivity, BiometricPrompt is null, so button is disabled
        // Verify it exists safely without NPE
    }

    @Test
    fun financeAppWithLockScreen_rendersContentWhenAppLockDisabled() {
        val settingsViewModel = mockk<SettingsViewModel>(relaxed = true)
        every { settingsViewModel.appLockEnabled } returns MutableStateFlow(false)

        composeTestRule.setContent {
            FinanceAppWithLockScreen(
                settingsViewModel = settingsViewModel,
            ) {
                Text("Inside Main App Content")
            }
        }

        composeTestRule.onNodeWithText("Inside Main App Content").assertExists()
    }

    @Test
    fun financeAppWithLockScreen_doesNotRenderContentWhenAppLockStateIsNull() {
        val settingsViewModel = mockk<SettingsViewModel>(relaxed = true)
        every { settingsViewModel.appLockEnabled } returns MutableStateFlow(null)

        composeTestRule.setContent {
            FinanceAppWithLockScreen(
                settingsViewModel = settingsViewModel,
            ) {
                Text("Inside Main App Content")
            }
        }

        composeTestRule.onNodeWithText("Inside Main App Content").assertDoesNotExist()
    }
}

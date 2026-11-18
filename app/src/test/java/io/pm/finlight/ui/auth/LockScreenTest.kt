package io.pm.finlight.ui.auth

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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

    private fun setFinanceAppContent(
        appLockEnabled: Boolean?,
        content: @Composable () -> Unit = { Text("Inside Main App Content") },
    ) {
        val settingsViewModel = mockk<SettingsViewModel>(relaxed = true)
        every { settingsViewModel.appLockEnabled } returns MutableStateFlow(appLockEnabled)

        composeTestRule.setContent {
            FinanceAppWithLockScreen(
                settingsViewModel = settingsViewModel,
                content = content,
            )
        }
    }

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
        setFinanceAppContent(appLockEnabled = false)

        composeTestRule.onNodeWithText("Inside Main App Content").assertExists()
    }

    @Test
    fun financeAppWithLockScreen_doesNotRenderContentWhenAppLockStateIsNull() {
        setFinanceAppContent(appLockEnabled = null)

        composeTestRule.onNodeWithText("Inside Main App Content").assertDoesNotExist()
    }

    @Test
    fun financeAppWithLockScreen_rendersLockScreenWhenAppLockEnabledAndCanAuthenticate() {
        val mockBiometricManager = mockk<BiometricManager>()
        every { mockBiometricManager.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_SUCCESS
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns mockBiometricManager

        try {
            setFinanceAppContent(appLockEnabled = true)

            composeTestRule.onNodeWithText("Unlock App").assertExists()
            composeTestRule.onNodeWithText("Inside Main App Content").assertDoesNotExist()
        } finally {
            unmockkStatic(BiometricManager::class)
        }
    }

    @Test
    fun financeAppWithLockScreen_rendersContentWhenAppLockEnabledButCannotAuthenticate() {
        val mockBiometricManager = mockk<BiometricManager>()
        every { mockBiometricManager.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns mockBiometricManager

        try {
            setFinanceAppContent(appLockEnabled = true)

            composeTestRule.onNodeWithText("Inside Main App Content").assertExists()
            composeTestRule.onNodeWithText("Unlock App").assertDoesNotExist()
        } finally {
            unmockkStatic(BiometricManager::class)
        }
    }

    @Test
    fun financeAppWithLockScreen_handlesBiometricManagerExceptionGracefully() {
        val mockBiometricManager = mockk<BiometricManager>()
        every { mockBiometricManager.canAuthenticate(any()) } throws RuntimeException("Hardware error")
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns mockBiometricManager

        try {
            setFinanceAppContent(appLockEnabled = true)

            composeTestRule.onNodeWithText("Inside Main App Content").assertExists()
            composeTestRule.onNodeWithText("Unlock App").assertDoesNotExist()
        } finally {
            unmockkStatic(BiometricManager::class)
        }
    }
}

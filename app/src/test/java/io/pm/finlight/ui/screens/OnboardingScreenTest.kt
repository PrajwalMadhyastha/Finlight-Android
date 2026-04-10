package io.pm.finlight.ui.screens

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.OnboardingViewModel
import io.pm.finlight.TestApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class OnboardingScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: OnboardingViewModel =
        mockk(relaxed = true) {
            every { userName } returns MutableStateFlow("Test User")
            every { monthlyBudget } returns MutableStateFlow("1000")
            every { homeCurrency } returns MutableStateFlow(null)
        }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun `next button is visible on notification page on Android 13 plus to allow skip`() {
        // Verify that the "Next" button is visible on page 4 (Notification) per the new logic

        composeTestRule.setContent {
            // We can test the OnboardingBottomBar directly to be more precise
            val pagerState = rememberPagerState(initialPage = 4) { 6 }
            OnboardingBottomBar(
                pagerState = pagerState,
                viewModel = viewModel,
                onNextClicked = {},
                onFinishClicked = {},
            )
        }

        // On page 4, "Next" should be visible (we removed the 'pagerState.currentPage != 4' hiding logic)
        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun `next button is hidden on SMS page to force interaction`() {
        // Verify that we still hide "Next" on page 3 (SMS) as requested/baseline

        composeTestRule.setContent {
            val pagerState = rememberPagerState(initialPage = 3) { 6 }
            OnboardingBottomBar(
                pagerState = pagerState,
                viewModel = viewModel,
                onNextClicked = {},
                onFinishClicked = {},
            )
        }

        // On page 3, "Next" should NOT be found
        composeTestRule.onNodeWithText("Next").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // Android 12
    fun `notification page content correctly shows Continue button on older android versions`() {
        var skipTriggered = false

        composeTestRule.setContent {
            NotificationPermissionPage(onPermissionResult = { skipTriggered = true })
        }

        // On Android < 13, we now show a "Continue" button instead of auto-skipping.
        assert(!skipTriggered)
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed().performClick()
        assert(skipTriggered)
    }
}

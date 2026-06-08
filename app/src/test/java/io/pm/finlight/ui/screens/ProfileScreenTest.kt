package io.pm.finlight.ui.screens

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.ProfileViewModel
import io.pm.finlight.TestApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ProfileScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `ProfileScreen displays app version from view model`() {
        val mockNavController: NavController = mockk(relaxed = true)
        val mockProfileViewModel: ProfileViewModel = mockk(relaxed = true)

        val fakeVersion = "1.2.3-test"

        // Mock state flows expected by ProfileScreen
        every { mockProfileViewModel.userName } returns MutableStateFlow("Test User")
        every { mockProfileViewModel.profilePictureUri } returns MutableStateFlow(null)
        // Mock app version property
        every { mockProfileViewModel.appVersion } returns fakeVersion

        composeTestRule.setContent {
            ProfileScreen(
                navController = mockNavController,
                profileViewModel = mockProfileViewModel,
            )
        }

        // Scroll to the version text since it's at the bottom of the LazyColumn
        composeTestRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(androidx.compose.ui.test.hasText("Version $fakeVersion"))

        // Verify that the version string is displayed
        composeTestRule.onNodeWithText("Version $fakeVersion").assertIsDisplayed()
    }
}

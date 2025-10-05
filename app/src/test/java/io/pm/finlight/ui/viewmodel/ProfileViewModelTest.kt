// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/ProfileViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.ProfileViewModel
import io.pm.finlight.SettingsRepository
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ProfileViewModelTest : BaseViewModelTest() {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: ProfileViewModel

    @Before
    override fun setup() {
        super.setup()
    }

    @Test
    fun `userName flows from repository`() = runTest {
        // Arrange
        val testName = "Test User"
        `when`(settingsRepository.getUserName()).thenReturn(flowOf(testName))
        `when`(settingsRepository.getProfilePictureUri()).thenReturn(flowOf(null))
        viewModel = ProfileViewModel(application, settingsRepository)

        // Assert
        viewModel.userName.test {
            assertEquals(testName, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `profilePictureUri flows from repository`() = runTest {
        // Arrange
        val testUri = "content://test/uri"
        `when`(settingsRepository.getUserName()).thenReturn(flowOf("User"))
        `when`(settingsRepository.getProfilePictureUri()).thenReturn(flowOf(testUri))
        viewModel = ProfileViewModel(application, settingsRepository)

        // Assert
        viewModel.profilePictureUri.test {
            assertEquals(testUri, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateUserName calls repository saveUserName`() = runTest {
        // Arrange
        val newName = "New Name"
        `when`(settingsRepository.getUserName()).thenReturn(flowOf("User"))
        `when`(settingsRepository.getProfilePictureUri()).thenReturn(flowOf(null))
        viewModel = ProfileViewModel(application, settingsRepository)

        // Act
        viewModel.updateUserName(newName)

        // Assert
        verify(settingsRepository).saveUserName(newName)
    }

    @Test
    fun `saveProfilePictureUri with null calls repository with null`() = runTest {
        // Arrange
        `when`(settingsRepository.getUserName()).thenReturn(flowOf("User"))
        `when`(settingsRepository.getProfilePictureUri()).thenReturn(flowOf(null))
        viewModel = ProfileViewModel(application, settingsRepository)

        // Act
        viewModel.saveProfilePictureUri(null)

        // Assert
        verify(settingsRepository).saveProfilePictureUri(null)
    }
}
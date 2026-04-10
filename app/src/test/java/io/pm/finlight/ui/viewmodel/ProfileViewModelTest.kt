// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/ui/viewmodel/ProfileViewModelTest.kt
// REASON: REFACTOR (Testing) - The test class now extends `BaseViewModelTest`,
// inheriting all common setup logic and removing boilerplate for rules,
// dispatchers, and Mockito initialization.
// UPDATE (Backup) - Added coverage for the new migrateProfilePictureIfNeeded()
// function and the profile/ directory storage path.
// =================================================================================
package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import java.io.File

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ProfileViewModelTest : BaseViewModelTest() {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var viewModel: ProfileViewModel

    /** Helper: shared prefs key used by the migration logic */
    private val prefs by lazy {
        application.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
    }

    private fun setupDefaultRepo() {
        `when`(settingsRepository.getUserName()).thenReturn(flowOf("User"))
        `when`(settingsRepository.getProfilePictureUri()).thenReturn(flowOf(null))
    }

    @Before
    override fun setup() {
        super.setup()
        // Clear prefs before each test so migration state is clean
        prefs.edit().clear().commit()
    }

    // ---- Existing tests ----

    @Test
    fun `userName flows from repository`() =
        runTest {
            val testName = "Test User"
            `when`(settingsRepository.getUserName()).thenReturn(flowOf(testName))
            `when`(settingsRepository.getProfilePictureUri()).thenReturn(flowOf(null))
            viewModel = ProfileViewModel(application, settingsRepository)

            viewModel.userName.test {
                assertEquals(testName, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `profilePictureUri flows from repository`() =
        runTest {
            val testUri = "content://test/uri"
            `when`(settingsRepository.getUserName()).thenReturn(flowOf("User"))
            `when`(settingsRepository.getProfilePictureUri()).thenReturn(flowOf(testUri))
            viewModel = ProfileViewModel(application, settingsRepository)

            viewModel.profilePictureUri.test {
                assertEquals(testUri, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `updateUserName calls repository saveUserName`() =
        runTest {
            val newName = "New Name"
            setupDefaultRepo()
            viewModel = ProfileViewModel(application, settingsRepository)

            viewModel.updateUserName(newName)

            verify(settingsRepository).saveUserName(newName)
        }

    @Test
    fun `saveProfilePictureUri with null calls repository with null`() =
        runTest {
            setupDefaultRepo()
            viewModel = ProfileViewModel(application, settingsRepository)

            viewModel.saveProfilePictureUri(null)

            verify(settingsRepository).saveProfilePictureUri(null)
        }

    // ---- Migration tests ----

    @Test
    fun `migration does nothing when no profile picture URI is stored in prefs`() =
        runTest {
            // Arrange: prefs has no profile_picture_uri key
            setupDefaultRepo()

            // Act
            viewModel = ProfileViewModel(application, settingsRepository)
            advanceUntilIdle()

            // Assert: saveProfilePictureUri should never be called during migration
            verify(settingsRepository, never()).saveProfilePictureUri(any())
        }

    @Test
    fun `migration does nothing when URI is not in the attachments directory`() =
        runTest {
            // Arrange: URI points to a non-attachments path
            val outsidePath = File(application.filesDir, "some_other_dir/profile_123.jpg").absolutePath
            prefs.edit().putString("profile_picture_uri", outsidePath).commit()
            setupDefaultRepo()

            // Act
            viewModel = ProfileViewModel(application, settingsRepository)
            advanceUntilIdle()

            // Assert: no migration should happen
            verify(settingsRepository, never()).saveProfilePictureUri(any())
        }

    @Test
    fun `migration does nothing when file in attachments directory does not exist`() =
        runTest {
            // Arrange: URI points into attachments/ but the actual file is gone (already deleted)
            val attachmentsDir = File(application.filesDir, "attachments")
            val nonExistentFile = File(attachmentsDir, "profile_ghost.jpg")
            prefs.edit().putString("profile_picture_uri", nonExistentFile.absolutePath).commit()
            setupDefaultRepo()

            // Act
            viewModel = ProfileViewModel(application, settingsRepository)
            advanceUntilIdle()

            // Assert: no migration attempted since the source file doesn't exist
            verify(settingsRepository, never()).saveProfilePictureUri(any())
        }

    @Test
    fun `migration moves profile picture from attachments to profile directory`() =
        runTest {
            // Arrange: create a real file in the old attachments/ directory
            val attachmentsDir = File(application.filesDir, "attachments").also { it.mkdirs() }
            val oldFile = File(attachmentsDir, "profile_12345.jpg").also { it.writeText("fake_image_data") }
            prefs.edit().putString("profile_picture_uri", oldFile.absolutePath).commit()
            setupDefaultRepo()

            // Act
            viewModel = ProfileViewModel(application, settingsRepository)
            // The migration dispatches to Dispatchers.IO (real thread pool); give it time to complete
            Thread.sleep(300)

            // Assert: repository updated with the new profile/ path
            val expectedNewPath = File(application.filesDir, "profile/${oldFile.name}").absolutePath
            val captor = ArgumentCaptor.forClass(String::class.java)
            verify(settingsRepository).saveProfilePictureUri(captor.capture())
            assertEquals("Repository should be updated with the new profile/ path", expectedNewPath, captor.value)
        }

    @Test
    fun `saveProfilePictureUri with non-null URI saves file to profile directory`() =
        runTest {
            // Arrange
            setupDefaultRepo()
            viewModel = ProfileViewModel(application, settingsRepository)

            // Write a tiny fake image to a temp file and expose it as a file URI
            val tempFile = File(application.cacheDir, "test_profile.jpg").also { it.writeText("fake") }
            val tempUri = Uri.fromFile(tempFile)

            // Act
            viewModel.saveProfilePictureUri(tempUri)
            // saveImageToInternalStorage dispatches to Dispatchers.IO; give it time to complete
            Thread.sleep(300)

            // Assert: repository was called with a path inside the profile/ directory
            val captor = ArgumentCaptor.forClass(String::class.java)
            verify(settingsRepository).saveProfilePictureUri(captor.capture())
            val savedPath = captor.value
            assertNotNull("Saved path should not be null", savedPath)
            assertTrue(
                "Saved path should be inside the profile/ directory",
                savedPath!!.contains("/profile/"),
            )
        }
}

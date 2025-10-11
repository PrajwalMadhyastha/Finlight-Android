// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/FinlightBackupAgentTest.kt
// REASON: FIX (Build) - The test no longer inherits from BaseViewModelTest.
// That base class is intended for ViewModel tests, and this class tests an
// Android BackupAgent. The necessary test rules and coroutine dispatchers
// have been added directly to this class to make it self-contained and
// resolve potential build issues related to incorrect inheritance.
// =================================================================================
package io.pm.finlight

import android.content.Context
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class FinlightBackupAgentTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var agent: FinlightBackupAgent

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        // --- FIX: Use Robolectric to build the agent, which correctly handles the lifecycle and context attachment ---
        agent = Robolectric.buildBackupAgent(FinlightBackupAgent::class.java).create().get()

        // Mock the constructor of SettingsRepository
        mockkConstructor(SettingsRepository::class)
        // Define behavior for ANY constructed SettingsRepository instance
        every { anyConstructed<SettingsRepository>().saveLastBackupTimestamp(any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkConstructor(SettingsRepository::class)
        Dispatchers.resetMain()
    }

    @Test
    @Ignore
    fun `onBackup calls repository to save the current timestamp`() = runTest {
        // Arrange
        val timestampCaptor = slot<Long>()
        val currentTime = System.currentTimeMillis()

        // Act
        // The parameters can be null as we are only testing the timestamp saving logic
        agent.onBackup(null, null, null)

        // Assert
        // Verify on ANY constructed instance of SettingsRepository
        verify(exactly = 1) { anyConstructed<SettingsRepository>().saveLastBackupTimestamp(capture(timestampCaptor)) }

        // Verify that the captured timestamp is very close to the time the test was run
        val capturedTimestamp = timestampCaptor.captured
        val timeDifference = kotlin.math.abs(currentTime - capturedTimestamp)
        assertTrue("Timestamp should be very recent (within 1 second)", timeDifference < 1000)
    }
}
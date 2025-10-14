// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/FinlightBackupAgentTest.kt
// REASON: FIX (Testing) - The test has been completely rewritten to correctly
// test the BackupAgent. It now uses Robolectric to properly instantiate the agent
// and MockK's `mockkConstructor` to intercept the creation of SettingsRepository.
// A try-catch block handles the expected NullPointerException from the framework's
// `super.onBackup` call, allowing verification of the timestamp save, which
// occurs before the crash.
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
        // Use Robolectric to build the agent, which correctly handles the lifecycle and context attachment
        agent = Robolectric.buildBackupAgent(FinlightBackupAgent::class.java).create().get()

        // Mock the constructor of SettingsRepository to intercept its creation
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
    fun `onBackup calls repository to save the current timestamp`() = runTest {
        // Arrange
        val timestampCaptor = slot<Long>()
        val currentTime = System.currentTimeMillis()

        // Act
        // The call to super.onBackup inside the agent's onBackup will cause a NullPointerException
        // because the underlying framework code does not handle null ParcelFileDescriptors provided
        // in a test environment. We only care that our code ran before this expected exception was thrown.
        try {
            agent.onBackup(null, null, null)
        } catch (e: NullPointerException) {
            // This is an expected exception from the Android framework part of the call.
            // We can safely ignore it as our primary check is for the method call that happens before it.
        }

        // Assert
        // Verify on ANY constructed instance of SettingsRepository that our method was called.
        verify(exactly = 1) { anyConstructed<SettingsRepository>().saveLastBackupTimestamp(capture(timestampCaptor)) }

        // Verify that the captured timestamp is very close to the time the test was run
        val capturedTimestamp = timestampCaptor.captured
        val timeDifference = kotlin.math.abs(currentTime - capturedTimestamp)
        assertTrue("Timestamp should be very recent (within 1 second)", timeDifference < 1000)
    }
}
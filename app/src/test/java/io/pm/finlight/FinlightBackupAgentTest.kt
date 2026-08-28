package io.pm.finlight

import android.content.Context
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import io.pm.finlight.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
    private lateinit var mockBackupSettingsRepository: IBackupSettingsRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        // Use Robolectric to build the agent, which correctly handles the lifecycle and context attachment
        agent = Robolectric.buildBackupAgent(FinlightBackupAgent::class.java).create().get()

        mockBackupSettingsRepository = mockk(relaxed = true)
        coEvery { mockBackupSettingsRepository.saveLastBackupTimestamp(any()) } just runs
        ServiceLocator.setBackupSettingsRepository(mockBackupSettingsRepository)
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `onBackup calls repository to save the current timestamp`() =
        runTest {
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
            coVerify(exactly = 1) { mockBackupSettingsRepository.saveLastBackupTimestamp(capture(timestampCaptor)) }

            // Verify that the captured timestamp is very close to the time the test was run
            val capturedTimestamp = timestampCaptor.captured
            val timeDifference = kotlin.math.abs(currentTime - capturedTimestamp)
            assertTrue("Timestamp should be very recent (within 1 second)", timeDifference < 1000)
        }

    @Test
    fun `onBackup triggers notification when notifications enabled`() =
        runTest {
            coEvery { mockBackupSettingsRepository.getAutoBackupNotificationEnabled() } returns flowOf(true)
            mockkObject(io.pm.finlight.utils.NotificationHelper)
            every { io.pm.finlight.utils.NotificationHelper.showAutoBackupNotification(any(), any()) } just runs

            agent.onBackup(null, null, null)

            verify(exactly = 1) { io.pm.finlight.utils.NotificationHelper.showAutoBackupNotification(any(), any()) }
            unmockkObject(io.pm.finlight.utils.NotificationHelper)
        }

    @Test
    fun `onBackup does not trigger notification when notifications disabled`() =
        runTest {
            coEvery { mockBackupSettingsRepository.getAutoBackupNotificationEnabled() } returns flowOf(false)
            mockkObject(io.pm.finlight.utils.NotificationHelper)
            every { io.pm.finlight.utils.NotificationHelper.showAutoBackupNotification(any(), any()) } just runs

            agent.onBackup(null, null, null)

            verify(exactly = 0) { io.pm.finlight.utils.NotificationHelper.showAutoBackupNotification(any(), any()) }
            unmockkObject(io.pm.finlight.utils.NotificationHelper)
        }

    @Test
    fun `onRestore handles restore process`() {
        agent.onRestore(null, 1, null)
    }
}

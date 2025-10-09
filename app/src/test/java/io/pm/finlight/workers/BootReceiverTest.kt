package io.pm.finlight.receiver

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.BootReceiver
import io.pm.finlight.TestApplication
import io.pm.finlight.utils.ReminderManager
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BootReceiverTest : BaseViewModelTest() {

    private lateinit var context: Context
    private lateinit var receiver: BootReceiver

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        receiver = BootReceiver()

        // Mock the static ReminderManager object
        mockkObject(ReminderManager)
        every { ReminderManager.rescheduleAllWork(any()) } just runs
    }

    @After
    override fun tearDown() {
        unmockkObject(ReminderManager)
        super.tearDown()
    }

    @Test
    fun `onReceive with BOOT_COMPLETED action calls ReminderManager`() {
        // Arrange
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // Act
        receiver.onReceive(context, intent)

        // Assert
        verify(exactly = 1) { ReminderManager.rescheduleAllWork(context) }
    }

    @Test
    fun `onReceive with other action does not call ReminderManager`() {
        // Arrange
        val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)

        // Act
        receiver.onReceive(context, intent)

        // Assert
        verify(exactly = 0) { ReminderManager.rescheduleAllWork(any()) }
    }
}

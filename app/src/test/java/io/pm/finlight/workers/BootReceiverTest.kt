package io.pm.finlight.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.BootReceiver
import io.pm.finlight.TestApplication
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.IWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BootReceiverTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var receiver: BootReceiver
    private val workScheduler: IWorkScheduler = mockk(relaxed = true)

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        receiver = BootReceiver()

        ServiceLocator.setApplicationScope(CoroutineScope(testDispatcher))
        ServiceLocator.setWorkScheduler(workScheduler)
    }

    @After
    override fun tearDown() {
        ServiceLocator.reset()
        super.tearDown()
    }

    @Test
    fun `onReceive with BOOT_COMPLETED action calls reconcileWorkOnStartup`() =
        runTest {
            val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

            receiver.onReceive(context, intent)

            coVerify(timeout = 3000) { workScheduler.reconcileWorkOnStartup() }
        }

    @Test
    fun `onReceive with MY_PACKAGE_REPLACED action calls reconcileWorkOnStartup`() =
        runTest {
            val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)

            receiver.onReceive(context, intent)

            coVerify(timeout = 3000) { workScheduler.reconcileWorkOnStartup() }
        }

    @Test
    fun `onReceive with other action does not call reconcileWorkOnStartup`() =
        runTest {
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)

            receiver.onReceive(context, intent)

            coVerify(exactly = 0) { workScheduler.reconcileWorkOnStartup() }
        }

    @Test
    fun `onReceive with null action does not call reconcileWorkOnStartup`() =
        runTest {
            val intent = Intent()

            receiver.onReceive(context, intent)

            coVerify(exactly = 0) { workScheduler.reconcileWorkOnStartup() }
        }

    @Test
    fun `onReceive with exception during BOOT_COMPLETED handles failure safely`() =
        runTest {
            coEvery { workScheduler.reconcileWorkOnStartup() } throws RuntimeException("Startup reconciliation failed")
            val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

            receiver.onReceive(context, intent)

            coVerify(timeout = 3000) { workScheduler.reconcileWorkOnStartup() }
        }

    @Test
    fun `onReceive with exception during MY_PACKAGE_REPLACED handles failure safely`() =
        runTest {
            coEvery { workScheduler.reconcileWorkOnStartup() } throws RuntimeException("Package replaced reconciliation failed")
            val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)

            receiver.onReceive(context, intent)

            coVerify(timeout = 3000) { workScheduler.reconcileWorkOnStartup() }
        }

    @Test
    fun `onReceive with non-null pendingResult finishes successfully on completion`() =
        runTest {
            val mockPendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
            val spiedReceiver = spyk(receiver)
            every { spiedReceiver.goAsync() } returns mockPendingResult
            val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

            spiedReceiver.onReceive(context, intent)

            coVerify(timeout = 3000) { workScheduler.reconcileWorkOnStartup() }
            verify(timeout = 3000) { mockPendingResult.finish() }
        }

    @Test
    fun `onReceive with non-null pendingResult finishes even when exception thrown`() =
        runTest {
            coEvery { workScheduler.reconcileWorkOnStartup() } throws RuntimeException("Boom")
            val mockPendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
            val spiedReceiver = spyk(receiver)
            every { spiedReceiver.goAsync() } returns mockPendingResult
            val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)

            spiedReceiver.onReceive(context, intent)

            coVerify(timeout = 3000) { workScheduler.reconcileWorkOnStartup() }
            verify(timeout = 3000) { mockPendingResult.finish() }
        }
}

// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/workers/SmsReceiverTest.kt
// REASON: REWRITE — SmsReceiver is now a thin dispatcher that only extracts
// SMS data and enqueues a SmsProcessorWorker. All tests now verify this single
// responsibility: one WorkManager job enqueued per unique SMS sender.
//
// Previous tests for parsing, account creation, travel mode, etc. have been
// moved to SmsProcessorWorkerTest and SmsTransactionSaverTest.
// =================================================================================
package io.pm.finlight.receiver

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage as AndroidSmsMessage
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.*
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.SmsReceiver
import io.pm.finlight.TestApplication
import io.pm.finlight.workers.SmsProcessorWorker
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE],
    application = TestApplication::class,
    shadows = [SmsReceiverTest.ShadowTelephonyIntents::class],
)
class SmsReceiverTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var receiver: SmsReceiver
    private lateinit var mockWorkManager: WorkManager

    @Implements(Telephony.Sms.Intents::class)
    object ShadowTelephonyIntents {
        var mockSmsMessages: Array<AndroidSmsMessage> = emptyArray()

        @JvmStatic
        @Implementation
        fun getMessagesFromIntent(intent: Intent?): Array<AndroidSmsMessage> = mockSmsMessages
    }

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        receiver = SmsReceiver()

        mockkObject(WorkManager)
        mockWorkManager = mockk(relaxed = true)
        every { WorkManager.getInstance(any()) } returns mockWorkManager
    }

    @After
    override fun tearDown() {
        ShadowTelephonyIntents.mockSmsMessages = emptyArray()
        unmockkAll()
        super.tearDown()
    }

    private fun createSmsIntent(
        sender: String,
        body: String
    ): Intent {
        val mockMessage = mockk<AndroidSmsMessage>()
        every { mockMessage.originatingAddress } returns sender
        every { mockMessage.messageBody } returns body
        every { mockMessage.timestampMillis } returns System.currentTimeMillis()
        ShadowTelephonyIntents.mockSmsMessages = arrayOf(mockMessage)
        return Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
    }

    // -------------------------------------------------------------------------
    // Core dispatch behaviour
    // -------------------------------------------------------------------------

    @Test
    fun `valid SMS enqueues SmsProcessorWorker`() {
        val intent = createSmsIntent("AM-HDFCBK", "Spent Rs.100 at Starbucks")
        receiver.onReceive(context, intent)
        verify(exactly = 1) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `wrong intent action does nothing`() {
        val intent = Intent("SOME_OTHER_ACTION")
        receiver.onReceive(context, intent)
        verify(exactly = 0) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `SMS with null sender is skipped`() {
        val mockMessage = mockk<AndroidSmsMessage>()
        every { mockMessage.originatingAddress } returns null
        every { mockMessage.messageBody } returns "Spent Rs.100"
        every { mockMessage.timestampMillis } returns 1L
        ShadowTelephonyIntents.mockSmsMessages = arrayOf(mockMessage)

        receiver.onReceive(context, Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))
        verify(exactly = 0) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `two different senders produce two separate worker enqueues`() {
        val msg1 = mockk<AndroidSmsMessage>()
        every { msg1.originatingAddress } returns "AM-HDFCBK"
        every { msg1.messageBody } returns "Spent Rs.100"
        every { msg1.timestampMillis } returns 1L

        val msg2 = mockk<AndroidSmsMessage>()
        every { msg2.originatingAddress } returns "AM-ICICI"
        every { msg2.messageBody } returns "Spent Rs.200"
        every { msg2.timestampMillis } returns 2L

        ShadowTelephonyIntents.mockSmsMessages = arrayOf(msg1, msg2)
        receiver.onReceive(context, Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))

        verify(exactly = 2) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `two messages from same sender are concatenated into one worker`() {
        val msg1 = mockk<AndroidSmsMessage>()
        every { msg1.originatingAddress } returns "AM-HDFCBK"
        every { msg1.messageBody } returns "Part 1 "
        every { msg1.timestampMillis } returns 1L

        val msg2 = mockk<AndroidSmsMessage>()
        every { msg2.originatingAddress } returns "AM-HDFCBK"
        every { msg2.messageBody } returns "Part 2"
        every { msg2.timestampMillis } returns 2L

        ShadowTelephonyIntents.mockSmsMessages = arrayOf(msg1, msg2)
        receiver.onReceive(context, Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))

        // Same sender → grouped → single enqueue
        verify(exactly = 1) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `worker input data contains sender body and date`() {
        val ts = 1717000000000L
        val mockMessage = mockk<AndroidSmsMessage>()
        every { mockMessage.originatingAddress } returns "AM-HDFCBK"
        every { mockMessage.messageBody } returns "Spent Rs.500 at Zomato"
        every { mockMessage.timestampMillis } returns ts
        ShadowTelephonyIntents.mockSmsMessages = arrayOf(mockMessage)

        val captor = slot<OneTimeWorkRequest>()
        every { mockWorkManager.enqueue(capture(captor)) } returns mockk(relaxed = true)

        receiver.onReceive(context, Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))

        val data = captor.captured.workSpec.input
        assert(data.getString(SmsProcessorWorker.KEY_SENDER) == "AM-HDFCBK")
        assert(data.getString(SmsProcessorWorker.KEY_BODY) == "Spent Rs.500 at Zomato")
        assert(data.getLong(SmsProcessorWorker.KEY_DATE, -1L) == ts)
    }
}

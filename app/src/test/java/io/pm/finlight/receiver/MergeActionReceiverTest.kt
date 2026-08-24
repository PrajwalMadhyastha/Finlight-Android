package io.pm.finlight.receiver

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.mockkStatic
import io.mockk.verify
import androidx.core.app.NotificationManagerCompat
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.TransactionDao
import io.pm.finlight.TransactionRepository
import io.pm.finlight.TransactionType
import io.pm.finlight.SmsRepository
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
class MergeActionReceiverTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var receiver: MergeActionReceiver
    private lateinit var mockNotificationManager: NotificationManagerCompat

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = mockk<AppDatabase>(relaxed = true)
        transactionDao = mockk<TransactionDao>(relaxed = true)
        transactionRepository = mockk<TransactionRepository>(relaxed = true)

        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.transactionDao() } returns transactionDao
        every { db.transactionQueryDao() } returns transactionDao
        every { db.transactionWriteDao() } returns transactionDao
        every { db.transactionAnalyticsDao() } returns transactionDao
        every { db.transactionReimbursementDao() } returns transactionDao
        // Normally we'd use koin, but we inject or mockk constructor if needed
        // For receiver which instantiates repository, we need to mockkConstructor
        io.mockk.mockkConstructor(TransactionRepository::class)
        io.mockk.mockkConstructor(SmsRepository::class)
        coEvery { anyConstructed<TransactionRepository>().mergeTransactions(any(), any(), any(), any()) } returns Unit
        coEvery { anyConstructed<TransactionRepository>().getTransactionSync(any()) } returns null
        coEvery { anyConstructed<TransactionRepository>().dismissMerge(any()) } returns Unit

        mockkStatic(NotificationManagerCompat::class)
        mockNotificationManager = mockk<NotificationManagerCompat>(relaxed = true)
        every { NotificationManagerCompat.from(any()) } returns mockNotificationManager

        receiver = MergeActionReceiver()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `ACTION_MERGE triggers mergeTransactions and cancels notification`() =
        runTest {
            val intent =
                Intent("ACTION_MERGE").apply {
                    putExtra("parentTxnId", 1)
                    putExtra("childTxnId", 2)
                    putExtra("notificationId", 10002)
                }

            receiver.onReceive(context, intent)

            coVerify(timeout = 2000) { anyConstructed<TransactionRepository>().mergeTransactions(1, 2, any(), any()) }
            verify(timeout = 2000) { mockNotificationManager.cancel(10002) }
            verify(timeout = 2000) { mockNotificationManager.cancel(2) }
        }

    @Test
    fun `ACTION_DISMISS triggers dismissMerge and cancels notification`() =
        runTest {
            val intent =
                Intent("ACTION_DISMISS").apply {
                    putExtra("childTxnId", 2)
                    putExtra("notificationId", 10002)
                }

            receiver.onReceive(context, intent)

            coVerify(timeout = 2000) { anyConstructed<TransactionRepository>().dismissMerge(2) }
            verify(timeout = 2000) { mockNotificationManager.cancel(10002) }
            verify(timeout = 2000) { mockNotificationManager.cancel(2) }
        }

    @Test
    fun `ACTION_MERGE fetches SMS if child transaction has sourceSmsId`() =
        runTest {
            val childTxn =
                io.pm.finlight.Transaction(
                    id = 2,
                    description = "Child",
                    amount = 100.0,
                    transactionType = TransactionType.EXPENSE,
                    date = 0L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    originalDescription = "Child",
                    sourceSmsId = 5
                )
            val sms = io.pm.finlight.SmsMessage(id = 5, sender = "Bank", body = "Test SMS body", date = 1000L)

            coEvery { anyConstructed<TransactionRepository>().getTransactionSync(2) } returns childTxn
            coEvery { anyConstructed<SmsRepository>().getSmsDetailsById(5) } returns sms

            val intent =
                Intent("ACTION_MERGE").apply {
                    putExtra("parentTxnId", 1)
                    putExtra("childTxnId", 2)
                    putExtra("notificationId", 10002)
                }

            receiver.onReceive(context, intent)

            coVerify(timeout = 2000) {
                anyConstructed<TransactionRepository>().mergeTransactions(1, 2, "Test SMS body", 1000L)
            }
        }
}

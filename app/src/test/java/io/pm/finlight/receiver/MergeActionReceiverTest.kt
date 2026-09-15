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
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TransactionType
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.MergeTransactionsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

import io.pm.finlight.BaseViewModelTest
import kotlinx.coroutines.delay

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MergeActionReceiverTest : BaseViewModelTest() {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var transactionWriteDao: TransactionWriteDao
    private lateinit var transactionQueryDao: TransactionQueryDao
    private lateinit var transactionAnalyticsDao: TransactionAnalyticsDao
    private lateinit var transactionReimbursementDao: TransactionReimbursementDao
    private lateinit var transactionRepository: ITransactionRepository
    private lateinit var smsRepository: ISmsRepository
    private lateinit var receiver: MergeActionReceiver
    private lateinit var mockNotificationManager: NotificationManagerCompat

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        db = mockk<AppDatabase>(relaxed = true)
        transactionWriteDao = mockk<TransactionWriteDao>(relaxed = true)
        transactionQueryDao = mockk<TransactionQueryDao>(relaxed = true)
        transactionAnalyticsDao = mockk<TransactionAnalyticsDao>(relaxed = true)
        transactionReimbursementDao = mockk<TransactionReimbursementDao>(relaxed = true)
        transactionRepository = mockk<ITransactionRepository>(relaxed = true)
        smsRepository = mockk<ISmsRepository>(relaxed = true)

        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.transactionQueryDao() } returns transactionQueryDao
        every { db.transactionWriteDao() } returns transactionWriteDao
        every { db.transactionAnalyticsDao() } returns transactionAnalyticsDao
        every { db.transactionReimbursementDao() } returns transactionReimbursementDao

        ServiceLocator.setTransactionRepository(transactionRepository)
        ServiceLocator.setSmsRepository(smsRepository)

        io.mockk.mockkConstructor(MergeTransactionsUseCase::class)

        coEvery { anyConstructed<MergeTransactionsUseCase>().invoke(any(), any(), any(), any()) } returns Unit
        coEvery { transactionRepository.getTransactionSync(any()) } returns null
        coEvery { transactionRepository.dismissMerge(any()) } returns Unit

        mockkStatic(NotificationManagerCompat::class)
        mockNotificationManager = mockk<NotificationManagerCompat>(relaxed = true)
        every { NotificationManagerCompat.from(any()) } returns mockNotificationManager

        receiver = MergeActionReceiver()
    }

    @After
    override fun tearDown() {
        ServiceLocator.reset()
        unmockkAll()
        super.tearDown()
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

            coVerify(timeout = 2000) { anyConstructed<MergeTransactionsUseCase>().invoke(1, 2, any(), any()) }
            verify(timeout = 2000) { mockNotificationManager.cancel(10002) }
            verify(timeout = 2000) { mockNotificationManager.cancel(2) }
            delay(100)
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

            coVerify(timeout = 2000) { transactionRepository.dismissMerge(2) }
            verify(timeout = 2000) { mockNotificationManager.cancel(10002) }
            verify(timeout = 2000) { mockNotificationManager.cancel(2) }
            delay(100)
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
                    sourceSmsId = 5,
                )
            val sms = io.pm.finlight.SmsMessage(id = 5, sender = "Bank", body = "Test SMS body", date = 1000L)

            coEvery { transactionRepository.getTransactionSync(2) } returns childTxn
            coEvery { smsRepository.getSmsDetailsById(5) } returns sms

            val intent =
                Intent("ACTION_MERGE").apply {
                    putExtra("parentTxnId", 1)
                    putExtra("childTxnId", 2)
                    putExtra("notificationId", 10002)
                }

            receiver.onReceive(context, intent)

            coVerify(timeout = 2000) {
                anyConstructed<MergeTransactionsUseCase>().invoke(1, 2, "Test SMS body", 1000L)
            }
            delay(100)
        }

    @Test
    fun `onReceive with null action does nothing`() =
        runTest {
            val intent = Intent()
            receiver.onReceive(context, intent)
            coVerify(exactly = 0) { anyConstructed<MergeTransactionsUseCase>().invoke(any(), any(), any(), any()) }
            coVerify(exactly = 0) { transactionRepository.dismissMerge(any()) }
        }

    @Test
    fun `onReceive with unknown action cancels notification if childId valid`() =
        runTest {
            val intent =
                Intent("UNKNOWN_ACTION").apply {
                    putExtra("childTxnId", 2)
                    putExtra("notificationId", 10002)
                }
            receiver.onReceive(context, intent)
            coVerify(exactly = 0) { anyConstructed<MergeTransactionsUseCase>().invoke(any(), any(), any(), any()) }
            coVerify(exactly = 0) { transactionRepository.dismissMerge(any()) }
            verify(timeout = 2000) { mockNotificationManager.cancel(10002) }
            verify(timeout = 2000) { mockNotificationManager.cancel(2) }
            delay(100)
        }

    @Test
    fun `ACTION_MERGE with invalid IDs does not trigger merge`() =
        runTest {
            val intent =
                Intent("ACTION_MERGE").apply {
                    putExtra("parentTxnId", -1)
                    putExtra("childTxnId", -1)
                }
            receiver.onReceive(context, intent)
            coVerify(exactly = 0) { anyConstructed<MergeTransactionsUseCase>().invoke(any(), any(), any(), any()) }
            delay(100)
        }

    @Test
    fun `ACTION_DISMISS with invalid childId does not call dismissMerge`() =
        runTest {
            val intent =
                Intent("ACTION_DISMISS").apply {
                    putExtra("childTxnId", -1)
                }
            receiver.onReceive(context, intent)
            coVerify(exactly = 0) { transactionRepository.dismissMerge(any()) }
            delay(100)
        }

    @Test
    fun `ACTION_MERGE handles missing SMS details gracefully`() =
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
                    sourceSmsId = 5,
                )
            coEvery { transactionRepository.getTransactionSync(2) } returns childTxn
            coEvery { smsRepository.getSmsDetailsById(5) } returns null

            val intent =
                Intent("ACTION_MERGE").apply {
                    putExtra("parentTxnId", 1)
                    putExtra("childTxnId", 2)
                    putExtra("notificationId", 10002)
                }

            receiver.onReceive(context, intent)

            coVerify(timeout = 2000) {
                anyConstructed<MergeTransactionsUseCase>().invoke(1, 2, null, null)
            }
            delay(100)
        }
}

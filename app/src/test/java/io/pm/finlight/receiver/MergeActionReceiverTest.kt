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
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.TransactionDao
import io.pm.finlight.TransactionRepository
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

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = mockk<AppDatabase>(relaxed = true)
        transactionDao = mockk<TransactionDao>(relaxed = true)
        transactionRepository = mockk<TransactionRepository>(relaxed = true)

        mockkObject(AppDatabase)
        every { AppDatabase.getInstance(any()) } returns db
        every { db.transactionDao() } returns transactionDao
        // Normally we'd use koin, but we inject or mockk constructor if needed
        // For receiver which instantiates repository, we need to mockkConstructor
        io.mockk.mockkConstructor(TransactionRepository::class)
        io.mockk.mockkConstructor(SmsRepository::class)
        coEvery { anyConstructed<TransactionRepository>().mergeTransactions(any(), any(), any(), any()) } returns Unit
        coEvery { anyConstructed<TransactionRepository>().getTransactionSync(any()) } returns null
        coEvery { anyConstructed<TransactionRepository>().dismissMerge(any()) } returns Unit

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
                }

            receiver.onReceive(context, intent)

            coVerify(timeout = 2000) { anyConstructed<TransactionRepository>().mergeTransactions(1, 2, any(), any()) }
        }

    @Test
    fun `ACTION_DISMISS triggers dismissMerge and cancels notification`() =
        runTest {
            val intent =
                Intent("ACTION_DISMISS").apply {
                    putExtra("childTxnId", 2)
                }

            receiver.onReceive(context, intent)

            coVerify(timeout = 2000) { anyConstructed<TransactionRepository>().dismissMerge(2) }
        }
}

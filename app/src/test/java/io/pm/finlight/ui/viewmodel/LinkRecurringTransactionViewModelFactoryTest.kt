package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.PotentialTransaction
import io.pm.finlight.TestApplication
import io.pm.finlight.di.ServiceLocator
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class LinkRecurringTransactionViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: LinkRecurringTransactionViewModelFactory
    private val potentialTxn =
        PotentialTransaction(
            sourceSmsId = 42L,
            smsSender = "RecurringSender",
            amount = 100.0,
            transactionType = "expense",
            merchantName = "Merchant",
            originalMessage = "Message",
        )

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        factory = LinkRecurringTransactionViewModelFactory(application, potentialTxn)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withLinkRecurringTransactionViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockRecurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setRecurringTransactionRepository(mockRecurringRepo)

        val viewModel = factory.create(LinkRecurringTransactionViewModel::class.java)

        assertNotNull(viewModel)
        val txnField =
            LinkRecurringTransactionViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnField)

        val recurringField =
            LinkRecurringTransactionViewModel::class.java.getDeclaredField("recurringTransactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRecurringRepo, recurringField)
        assertSame(potentialTxn, viewModel.potentialTransaction)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}

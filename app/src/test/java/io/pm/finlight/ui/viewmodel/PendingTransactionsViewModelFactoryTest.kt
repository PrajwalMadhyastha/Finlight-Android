package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.IRecurringTransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider
import kotlinx.coroutines.flow.flowOf
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
class PendingTransactionsViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: PendingTransactionsViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory = PendingTransactionsViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withPendingTransactionsViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockRecurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)
        every { mockTxnRepo.getPendingTransactionsFlow() } returns flowOf(emptyList())

        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setRecurringTransactionRepository(mockRecurringRepo)

        val viewModel = factory.create(PendingTransactionsViewModel::class.java)

        assertNotNull(viewModel)

        val txnRepoField =
            PendingTransactionsViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnRepoField)

        val recurringRepoField =
            PendingTransactionsViewModel::class.java.getDeclaredField("recurringTransactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRecurringRepo, recurringRepoField)
    }

    @Test
    fun create_withExplicitDependencies_usesProvidedDependencies() {
        val customTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val customRecurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)
        val customDispatcher: DispatcherProvider = mockk(relaxed = true)
        every { customTxnRepo.getPendingTransactionsFlow() } returns flowOf(emptyList())

        val explicitFactory =
            PendingTransactionsViewModelFactory(
                application = application,
                transactionRepository = customTxnRepo,
                recurringTransactionRepository = customRecurringRepo,
                dispatcherProvider = customDispatcher,
            )

        val viewModel = explicitFactory.create(PendingTransactionsViewModel::class.java)

        assertNotNull(viewModel)

        val txnRepoField =
            PendingTransactionsViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(customTxnRepo, txnRepoField)

        val recurringRepoField =
            PendingTransactionsViewModel::class.java.getDeclaredField("recurringTransactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(customRecurringRepo, recurringRepoField)

        val dispatcherField =
            PendingTransactionsViewModel::class.java.getDeclaredField("dispatcherProvider").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(customDispatcher, dispatcherField)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}

package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import io.pm.finlight.DashboardViewModel
import io.pm.finlight.DashboardViewModelFactory
import io.pm.finlight.IAccountRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
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
class DashboardViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: DashboardViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory = DashboardViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withDashboardViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockAccountRepo: IAccountRepository = mockk(relaxed = true)

        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setAccountRepository(mockAccountRepo)

        val viewModel = factory.create(DashboardViewModel::class.java)

        assertNotNull(viewModel)
        val txnField =
            DashboardViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnField)

        val accField =
            DashboardViewModel::class.java.getDeclaredField("accountRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockAccountRepo, accField)

        verify { mockTxnRepo.getFinancialSummaryForRangeFlow(any(), any()) }
        verify { mockAccountRepo.accountsWithBalance }
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}

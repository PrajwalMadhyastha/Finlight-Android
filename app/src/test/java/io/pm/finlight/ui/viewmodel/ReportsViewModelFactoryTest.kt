package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.ReportsViewModel
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase
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
class ReportsViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: ReportsViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory = ReportsViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withReportsViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val viewModel = factory.create(ReportsViewModel::class.java)

        assertNotNull(viewModel)
        val txnField =
            ReportsViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnField)

        val useCase =
            ReportsViewModel::class.java.getDeclaredField("getMonthlyConsistencyDataUseCase").apply {
                isAccessible = true
            }.get(viewModel) as GetMonthlyConsistencyDataUseCase
        assertNotNull(useCase)

        val useCaseTxnRepo =
            GetMonthlyConsistencyDataUseCase::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(useCase)
        assertSame(mockTxnRepo, useCaseTxnRepo)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}

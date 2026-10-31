package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.repository.ITripRepository
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
class HistoricTripsViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: HistoricTripsViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory = HistoricTripsViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withHistoricTripsViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockTripRepo: ITripRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)

        ServiceLocator.setTripRepository(mockTripRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val viewModel = factory.create(HistoricTripsViewModel::class.java)

        assertNotNull(viewModel)

        val tripField =
            HistoricTripsViewModel::class.java.getDeclaredField("tripRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTripRepo, tripField)

        val txnField =
            HistoricTripsViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnField)
    }

    @Test
    fun create_withExtras_returnsHistoricTripsViewModel() {
        val viewModel = factory.create(HistoricTripsViewModel::class.java, CreationExtras.Empty)
        assertNotNull(viewModel)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockViewModel: HistoricTripsViewModel = mockk(relaxed = true)
        ServiceLocator.setHistoricTripsViewModel(mockViewModel)

        val resolved = factory.create(HistoricTripsViewModel::class.java)
        assertSame(mockViewModel, resolved)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}


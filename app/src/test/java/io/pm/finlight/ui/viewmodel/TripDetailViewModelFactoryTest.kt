package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
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
class TripDetailViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: TripDetailViewModelFactory
    private val testTripId = 7
    private val testTagId = 14

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        factory = TripDetailViewModelFactory(application, testTripId, testTagId)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withTripDetailViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockTripRepo: ITripRepository = mockk(relaxed = true)
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)

        ServiceLocator.setTripRepository(mockTripRepo)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val viewModel = factory.create(TripDetailViewModel::class.java)
        assertNotNull(viewModel)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }

    @Test
    fun create_withExtras_returnsTripDetailViewModel() {
        val vm = factory.create(TripDetailViewModel::class.java, CreationExtras.Empty)
        assertNotNull(vm)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockVM: TripDetailViewModel = mockk(relaxed = true)
        ServiceLocator.setTripDetailViewModel(mockVM)

        val resolved = factory.create(TripDetailViewModel::class.java)
        assertSame(mockVM, resolved)
    }

    @Test
    fun create_withExplicitOverrides_usesPassedDependencies() {
        val customTripRepo: ITripRepository = mockk(relaxed = true)
        val customTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val customFactory =
            TripDetailViewModelFactory(
                application = application,
                tripId = testTripId,
                tagId = testTagId,
                tripRepository = customTripRepo,
                transactionRepository = customTxnRepo,
            )

        val vm = customFactory.create(TripDetailViewModel::class.java)
        assertNotNull(vm)
    }
}

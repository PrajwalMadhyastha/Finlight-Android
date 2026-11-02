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
class DrilldownViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: DrilldownViewModelFactory

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        factory =
            DrilldownViewModelFactory(
                application = application,
                drilldownType = DrilldownType.CATEGORY,
                entityName = "Food",
                month = 1,
                year = 2026,
            )
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withDrilldownViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        ServiceLocator.setTransactionRepository(mockTxnRepo)

        val viewModel = factory.create(DrilldownViewModel::class.java)

        assertNotNull(viewModel)
        val txnField =
            DrilldownViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnField)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }

    @Test
    fun create_withExtras_returnsDrilldownViewModel() {
        val vm = factory.create(DrilldownViewModel::class.java, CreationExtras.Empty)
        assertNotNull(vm)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockVM: DrilldownViewModel = mockk(relaxed = true)
        ServiceLocator.setDrilldownViewModel(mockVM)

        val resolved = factory.create(DrilldownViewModel::class.java)
        assertSame(mockVM, resolved)
    }

    @Test
    fun create_withExplicitOverrides_usesPassedDependencies() {
        val customTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val customFactory =
            DrilldownViewModelFactory(
                application = application,
                drilldownType = DrilldownType.MERCHANT,
                entityName = "Market",
                month = 4,
                year = 2026,
                transactionRepository = customTxnRepo,
            )

        val vm = customFactory.create(DrilldownViewModel::class.java)
        assertNotNull(vm)
    }
}

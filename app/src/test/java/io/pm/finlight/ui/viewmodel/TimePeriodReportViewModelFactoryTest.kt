package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.model.TimePeriod
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.di.assertThrowsUnknownViewModel
import io.pm.finlight.domain.usecase.GetMonthlyConsistencyDataUseCase
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TimePeriodReportViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: TimePeriodReportViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory =
            TimePeriodReportViewModelFactory(
                application = application,
                timePeriod = TimePeriod.MONTHLY,
                initialDateMillis = null,
                showPreviousMonth = false,
            )
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withTimePeriodReportViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val mockUseCase: GetMonthlyConsistencyDataUseCase = mockk(relaxed = true)
        ServiceLocator.setTransactionRepository(mockTxnRepo)
        ServiceLocator.setGetMonthlyConsistencyDataUseCase(mockUseCase)

        val viewModel = factory.create(TimePeriodReportViewModel::class.java)

        assertNotNull(viewModel)
        val txnField =
            TimePeriodReportViewModel::class.java.getDeclaredField("transactionRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockTxnRepo, txnField)

        val useCase =
            TimePeriodReportViewModel::class.java.getDeclaredField("getMonthlyConsistencyDataUseCase").apply {
                isAccessible = true
            }.get(viewModel) as GetMonthlyConsistencyDataUseCase
        assertSame(mockUseCase, useCase)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        assertThrowsUnknownViewModel(factory)
    }

    @Test
    fun create_withExtras_returnsTimePeriodReportViewModel() {
        val vm = factory.create(TimePeriodReportViewModel::class.java, CreationExtras.Empty)
        assertNotNull(vm)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockVM: TimePeriodReportViewModel = mockk(relaxed = true)
        ServiceLocator.setTimePeriodReportViewModel(mockVM)

        val resolved = factory.create(TimePeriodReportViewModel::class.java)
        assertSame(mockVM, resolved)
    }

    @Test
    fun create_withExplicitOverrides_usesPassedDependencies() {
        val customTxnRepo: ITransactionRepository = mockk(relaxed = true)
        val customUseCase: GetMonthlyConsistencyDataUseCase = mockk(relaxed = true)
        val customFactory =
            TimePeriodReportViewModelFactory(
                application = application,
                timePeriod = TimePeriod.MONTHLY,
                initialDateMillis = 100L,
                showPreviousMonth = true,
                transactionRepository = customTxnRepo,
                getMonthlyConsistencyDataUseCase = customUseCase,
            )

        val vm = customFactory.create(TimePeriodReportViewModel::class.java)
        assertNotNull(vm)
    }
}

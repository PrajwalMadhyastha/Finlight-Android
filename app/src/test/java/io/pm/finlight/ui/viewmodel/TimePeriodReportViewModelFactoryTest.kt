package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.model.TimePeriod
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
class TimePeriodReportViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: TimePeriodReportViewModelFactory

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
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
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}

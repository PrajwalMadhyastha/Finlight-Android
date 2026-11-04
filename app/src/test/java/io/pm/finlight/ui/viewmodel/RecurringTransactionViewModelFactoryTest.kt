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
import io.pm.finlight.RecurringTransactionViewModel
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
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
class RecurringTransactionViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: RecurringTransactionViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory = RecurringTransactionViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withRecurringTransactionViewModelClass_resolvesRepositoryFromServiceLocator() {
        val mockRecurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)
        val mockScheduleWorkerUseCase: io.pm.finlight.domain.usecase.ScheduleRecurringTransactionWorkerUseCase = mockk(relaxed = true)
        every { mockRecurringRepo.getAll() } returns flowOf(emptyList())

        ServiceLocator.setRecurringTransactionRepository(mockRecurringRepo)
        ServiceLocator.setScheduleRecurringTransactionWorkerUseCase(mockScheduleWorkerUseCase)

        val viewModel = factory.create(RecurringTransactionViewModel::class.java)

        assertNotNull(viewModel)

        val repoField =
            RecurringTransactionViewModel::class.java.getDeclaredField("repository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRecurringRepo, repoField)

        val useCaseField =
            RecurringTransactionViewModel::class.java.getDeclaredField("scheduleWorkerUseCase").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockScheduleWorkerUseCase, useCaseField)
    }

    @Test
    fun create_withExtras_returnsRecurringTransactionViewModel() {
        val mockRecurringRepo: IRecurringTransactionRepository = mockk(relaxed = true)
        val mockScheduleWorkerUseCase: io.pm.finlight.domain.usecase.ScheduleRecurringTransactionWorkerUseCase = mockk(relaxed = true)
        every { mockRecurringRepo.getAll() } returns flowOf(emptyList())

        ServiceLocator.setRecurringTransactionRepository(mockRecurringRepo)
        ServiceLocator.setScheduleRecurringTransactionWorkerUseCase(mockScheduleWorkerUseCase)

        val viewModel = factory.create(RecurringTransactionViewModel::class.java, androidx.lifecycle.viewmodel.CreationExtras.Empty)
        assertNotNull(viewModel)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockViewModel: RecurringTransactionViewModel = mockk(relaxed = true)
        ServiceLocator.setRecurringTransactionViewModel(mockViewModel)

        val resolved = factory.create(RecurringTransactionViewModel::class.java)
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

package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.pm.finlight.ISmsRepository
import io.pm.finlight.SmsDebugViewModel
import io.pm.finlight.TestApplication
import io.pm.finlight.TransactionViewModel
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.ml.MlModelFactory
import io.pm.finlight.ml.NerExtractor
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.ml.SmsEntityExtractor
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
class SmsDebugViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
        unmockkAll()
    }

    @Test
    fun create_withSmsDebugViewModelClass_resolvesDependenciesAndDoesNotInstantiateNerExtractor() {
        val mockSmsRepo: ISmsRepository =
            mockk(relaxed = true) {
                coEvery { fetchAllSms(any()) } returns emptyList()
            }
        ServiceLocator.setSmsRepository(mockSmsRepo)

        mockkObject(MlModelFactory)
        val mockClassifier: SmsClassifier = mockk(relaxed = true)
        val mockNerExtractor: NerExtractor = mockk(relaxed = true)
        every { MlModelFactory.getClassifier(any()) } returns mockClassifier
        every { MlModelFactory.getNerExtractor(any()) } returns mockNerExtractor

        val mockTransactionViewModel: TransactionViewModel = mockk(relaxed = true)
        val factory = SmsDebugViewModelFactory(application, mockTransactionViewModel)

        val viewModel = factory.create(SmsDebugViewModel::class.java)

        assertNotNull(viewModel)

        // NerExtractor is NOT eagerly instantiated on creation
        verify(exactly = 0) { MlModelFactory.getNerExtractor(any()) }

        // When provider is invoked on-demand, NerExtractor is loaded
        val resolvedExtractor = viewModel.nerExtractorProvider.invoke()
        assertSame(mockNerExtractor, resolvedExtractor)
        verify(exactly = 1) { MlModelFactory.getNerExtractor(any()) }
    }

    @Test
    fun create_withCustomNerExtractorProvider_passesProviderToViewModel() {
        val mockSmsRepo: ISmsRepository =
            mockk(relaxed = true) {
                coEvery { fetchAllSms(any()) } returns emptyList()
            }
        ServiceLocator.setSmsRepository(mockSmsRepo)

        mockkObject(MlModelFactory)
        val mockClassifier: SmsClassifier = mockk(relaxed = true)
        every { MlModelFactory.getClassifier(any()) } returns mockClassifier

        val customExtractor: SmsEntityExtractor = mockk(relaxed = true)
        val customProvider: () -> SmsEntityExtractor = { customExtractor }

        val mockTransactionViewModel: TransactionViewModel = mockk(relaxed = true)
        val factory =
            SmsDebugViewModelFactory(
                application,
                mockTransactionViewModel,
                nerExtractorProvider = customProvider,
            )
        val viewModel = factory.create(SmsDebugViewModel::class.java)

        assertSame(customExtractor, viewModel.nerExtractorProvider.invoke())
        verify(exactly = 0) { MlModelFactory.getNerExtractor(any()) }
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        val mockTransactionViewModel: TransactionViewModel = mockk(relaxed = true)
        val factory = SmsDebugViewModelFactory(application, mockTransactionViewModel)

        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}

package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider
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
class RuleCreationViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: RuleCreationViewModelFactory
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        AppDatabase.setTestInstance(db)
        factory = RuleCreationViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        db.close()
        ServiceLocator.reset()
    }

    @Test
    fun create_withRuleCreationViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockRepo: ICustomSmsRuleRepository = mockk(relaxed = true)
        val mockDispatchers: DispatcherProvider = mockk(relaxed = true)

        ServiceLocator.setCustomSmsRuleRepository(mockRepo)
        ServiceLocator.setDispatcherProvider(mockDispatchers)

        val viewModel = factory.create(RuleCreationViewModel::class.java)

        assertNotNull(viewModel)

        val repoField =
            RuleCreationViewModel::class.java.getDeclaredField("customSmsRuleRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRepo, repoField)

        val dispatchersField =
            RuleCreationViewModel::class.java.getDeclaredField("dispatcherProvider").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockDispatchers, dispatchersField)
    }

    @Test
    fun create_withCustomInjectedDependencies_usesExplicitInstances() {
        val customRepo: ICustomSmsRuleRepository = mockk(relaxed = true)
        val customDispatchers: DispatcherProvider = mockk(relaxed = true)
        val customFactory =
            RuleCreationViewModelFactory(
                application = application,
                customSmsRuleRepository = customRepo,
                dispatcherProvider = customDispatchers,
            )

        val viewModel = customFactory.create(RuleCreationViewModel::class.java)

        assertNotNull(viewModel)

        val repoField =
            RuleCreationViewModel::class.java.getDeclaredField("customSmsRuleRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(customRepo, repoField)

        val dispatchersField =
            RuleCreationViewModel::class.java.getDeclaredField("dispatcherProvider").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(customDispatchers, dispatchersField)
    }

    @Test
    fun create_withUnknownViewModelClass_throwsIllegalArgumentException() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }

    @Test
    fun create_withExtras_returnsRuleCreationViewModel() {
        val viewModel = factory.create(RuleCreationViewModel::class.java, CreationExtras.Empty)
        assertNotNull(viewModel)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockViewModel: RuleCreationViewModel = mockk(relaxed = true)
        ServiceLocator.setRuleCreationViewModel(mockViewModel)

        val resolved = factory.create(RuleCreationViewModel::class.java)
        assertSame(mockViewModel, resolved)
    }

    @Test
    fun create_withCustomSavedStateHandle_usesExplicitInstance() {
        val customHandle = SavedStateHandle(mapOf("key" to "value"))
        val customFactory =
            RuleCreationViewModelFactory(
                application = application,
                savedStateHandle = customHandle,
            )

        val viewModel = customFactory.create(RuleCreationViewModel::class.java)
        assertNotNull(viewModel)
        assertSame(customHandle, viewModel.savedStateHandle)
    }
}

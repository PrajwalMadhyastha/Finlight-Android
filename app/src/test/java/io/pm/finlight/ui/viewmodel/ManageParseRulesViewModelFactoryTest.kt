package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.ICustomSmsRuleRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.di.ServiceLocator
import io.pm.finlight.utils.DispatcherProvider
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
class ManageParseRulesViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: ManageParseRulesViewModelFactory

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        factory = ManageParseRulesViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withManageParseRulesViewModelClass_resolvesDependenciesFromServiceLocator() {
        val mockRepo: ICustomSmsRuleRepository = mockk(relaxed = true)
        val mockDispatchers: DispatcherProvider = mockk(relaxed = true)

        every { mockRepo.getAllRules() } returns flowOf(emptyList())

        ServiceLocator.setCustomSmsRuleRepository(mockRepo)
        ServiceLocator.setDispatcherProvider(mockDispatchers)

        val viewModel = factory.create(ManageParseRulesViewModel::class.java)

        assertNotNull(viewModel)

        val repoField =
            ManageParseRulesViewModel::class.java.getDeclaredField("customSmsRuleRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockRepo, repoField)

        val dispatchersField =
            ManageParseRulesViewModel::class.java.getDeclaredField("dispatcherProvider").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockDispatchers, dispatchersField)
    }

    @Test
    fun create_withCustomInjectedDependencies_usesExplicitInstances() {
        val customRepo: ICustomSmsRuleRepository = mockk(relaxed = true)
        val customDispatchers: DispatcherProvider = mockk(relaxed = true)

        every { customRepo.getAllRules() } returns flowOf(emptyList())

        val customFactory =
            ManageParseRulesViewModelFactory(
                application = application,
                customSmsRuleRepository = customRepo,
                dispatcherProvider = customDispatchers,
            )

        val viewModel = customFactory.create(ManageParseRulesViewModel::class.java)

        assertNotNull(viewModel)

        val repoField =
            ManageParseRulesViewModel::class.java.getDeclaredField("customSmsRuleRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(customRepo, repoField)

        val dispatchersField =
            ManageParseRulesViewModel::class.java.getDeclaredField("dispatcherProvider").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(customDispatchers, dispatchersField)
    }

    @Test
    fun create_withExtras_returnsManageParseRulesViewModel() {
        val viewModel = factory.create(ManageParseRulesViewModel::class.java, CreationExtras.Empty)
        assertNotNull(viewModel)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockViewModel: ManageParseRulesViewModel = mockk(relaxed = true)
        ServiceLocator.setManageParseRulesViewModel(mockViewModel)

        val resolved = factory.create(ManageParseRulesViewModel::class.java)
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

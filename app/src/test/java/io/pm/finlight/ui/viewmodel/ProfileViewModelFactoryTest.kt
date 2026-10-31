package io.pm.finlight.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.pm.finlight.ISettingsRepository
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
class ProfileViewModelFactoryTest {
    private lateinit var application: Application
    private lateinit var factory: ProfileViewModelFactory

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        factory = ProfileViewModelFactory(application)
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun create_withProfileViewModelClass_resolvesRepositoriesFromServiceLocator() {
        val mockSettingsRepo: ISettingsRepository = mockk(relaxed = true)
        ServiceLocator.setSettingsRepository(mockSettingsRepo)

        val viewModel = factory.create(ProfileViewModel::class.java)

        assertNotNull(viewModel)

        val settingsField =
            ProfileViewModel::class.java.getDeclaredField("settingsRepository").apply {
                isAccessible = true
            }.get(viewModel)
        assertSame(mockSettingsRepo, settingsField)
    }

    @Test
    fun create_withExtras_returnsProfileViewModel() {
        val viewModel = factory.create(ProfileViewModel::class.java, CreationExtras.Empty)
        assertNotNull(viewModel)
    }

    @Test
    fun create_withOverriddenViewModel_returnsMockedInstance() {
        val mockViewModel: ProfileViewModel = mockk(relaxed = true)
        ServiceLocator.setProfileViewModel(mockViewModel)

        val resolved = factory.create(ProfileViewModel::class.java)
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

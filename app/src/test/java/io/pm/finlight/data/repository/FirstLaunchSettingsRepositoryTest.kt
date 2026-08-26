package io.pm.finlight.data.repository

import android.app.Application
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.FirstLaunchSettingsRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.financeSettingsDataStore
import io.pm.finlight.data.internalSettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class FirstLaunchSettingsRepositoryTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var repository: FirstLaunchSettingsRepository

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val dataStore = context.financeSettingsDataStore
        val internalDataStore = context.internalSettingsDataStore
        runTest {
            dataStore.edit { it.clear() }
            internalDataStore.edit { it.clear() }
        }
        repository = FirstLaunchSettingsRepository(dataStore, internalDataStore)
    }

    @Test
    fun `hasSeenOnboarding Flow and setHasSeenOnboarding work correctly`() =
        runTest {
            repository.getHasSeenOnboarding().test {
                assertFalse(awaitItem())
                repository.setHasSeenOnboarding(true)
                assertTrue(awaitItem())
                repository.setHasSeenOnboarding(false)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getIsFirstLaunchComplete and setFirstLaunchComplete work correctly`() =
        runTest {
            repository.getIsFirstLaunchComplete().test {
                assertFalse(awaitItem())
                repository.setFirstLaunchComplete()
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}


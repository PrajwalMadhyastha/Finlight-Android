package io.pm.finlight.data.repository

import android.app.Application
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun `hasSeenOnboarding and setHasSeenOnboarding work correctly`() =
        runTest {
            assertFalse(repository.hasSeenOnboarding())
            repository.setHasSeenOnboarding(true)
            assertTrue(repository.hasSeenOnboarding())
            repository.setHasSeenOnboarding(false)
            assertFalse(repository.hasSeenOnboarding())
        }

    @Test
    fun `isFirstLaunchCompleteBlocking and setFirstLaunchComplete work correctly`() =
        runTest {
            assertFalse(repository.isFirstLaunchCompleteBlocking())
            repository.setFirstLaunchComplete()
            assertTrue(repository.isFirstLaunchCompleteBlocking())
        }
}

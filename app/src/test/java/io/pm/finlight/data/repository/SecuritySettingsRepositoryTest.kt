package io.pm.finlight.data.repository

import android.app.Application
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.SecuritySettingsRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SecuritySettingsRepositoryTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var repository: SecuritySettingsRepository

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val dataStore = context.financeSettingsDataStore
        runTest {
            dataStore.edit { it.clear() }
        }
        repository = SecuritySettingsRepository(dataStore)
    }

    @Test
    fun `save and get app lock enabled`() =
        runTest {
            repository.getAppLockEnabled().test {
                assertEquals(false, awaitItem()) // Default
                repository.saveAppLockEnabled(true)
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get privacy mode enabled`() =
        runTest {
            repository.getPrivacyModeEnabled().test {
                assertEquals(false, awaitItem()) // Default
                repository.savePrivacyModeEnabled(true)
                assertEquals(true, awaitItem())
                repository.savePrivacyModeEnabled(false)
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get simulator privacy mode enabled`() =
        runTest {
            repository.getSimulatorPrivacyModeEnabled().test {
                assertEquals(false, awaitItem()) // Default
                repository.saveSimulatorPrivacyModeEnabled(true)
                assertEquals(true, awaitItem())
                repository.saveSimulatorPrivacyModeEnabled(false)
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

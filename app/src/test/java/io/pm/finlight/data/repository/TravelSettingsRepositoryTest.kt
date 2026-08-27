package io.pm.finlight.data.repository

import android.app.Application
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TravelSettingsRepository
import io.pm.finlight.TripType
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TravelSettingsRepositoryTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var repository: TravelSettingsRepository
    private val gson = Gson()

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val dataStore = context.financeSettingsDataStore
        runTest {
            dataStore.edit { it.clear() }
        }
        repository = TravelSettingsRepository(dataStore)
    }

    @Test
    fun `save and get travel mode settings`() =
        runTest {
            val futureEndDate = System.currentTimeMillis() + 100000L
            val settings = TravelModeSettings(true, "US Trip", TripType.INTERNATIONAL, 1L, futureEndDate, "USD", 83.5f)

            repository.getTravelModeSettings().test {
                assertNull(awaitItem()) // Initial
                repository.saveTravelModeSettings(settings)
                assertEquals(settings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getTravelModeSettings auto-expires past trips`() =
        runTest {
            val pastEndDate = 0L
            val expiredSettings = TravelModeSettings(true, "Old Trip", TripType.DOMESTIC, 1L, pastEndDate, null, null)

            // Inject past expired trip directly
            val prefKey = stringPreferencesKey("travel_mode_settings")
            context.financeSettingsDataStore.edit {
                it[prefKey] = gson.toJson(expiredSettings)
            }

            repository.getTravelModeSettings().test {
                val item = awaitItem()
                assertNull(item)
                cancelAndIgnoreRemainingEvents()
            }

            // Verify it was cleared from preferences
            assertNull(context.financeSettingsDataStore.data.first()[prefKey])
        }

    @Test
    fun `getCurrentTravelModeSettings returns null when empty`() =
        runTest {
            val result = repository.getCurrentTravelModeSettings()
            assertNull(result)
        }

    @Test
    fun `getCurrentTravelModeSettings returns saved settings`() =
        runTest {
            val futureEndDate = System.currentTimeMillis() + 100000L
            val settings = TravelModeSettings(true, "US Trip", TripType.INTERNATIONAL, 1L, futureEndDate, "USD", 83.5f)

            repository.saveTravelModeSettings(settings)
            val result = repository.getCurrentTravelModeSettings()

            assertEquals(settings, result)
        }

    @Test
    fun `getCurrentTravelModeSettings does not mutate DataStore when trip is expired`() =
        runTest {
            val pastEndDate = 0L
            val expiredSettings = TravelModeSettings(true, "Old Trip", TripType.DOMESTIC, 1L, pastEndDate, null, null)

            val prefKey = stringPreferencesKey("travel_mode_settings")
            context.financeSettingsDataStore.edit {
                it[prefKey] = gson.toJson(expiredSettings)
            }

            val result = repository.getCurrentTravelModeSettings()
            assertEquals(expiredSettings, result)

            // Verify DataStore was NOT mutated (preserving Query-Command Separation)
            assertEquals(gson.toJson(expiredSettings), context.financeSettingsDataStore.data.first()[prefKey])
        }

    @Test
    fun `getCurrentTravelModeSettings returns null on IOException`() =
        runTest {
            val mockDataStore: DataStore<Preferences> = mockk()
            every { mockDataStore.data } returns flow { throw IOException("Disk read failure") }
            val repo = TravelSettingsRepository(mockDataStore)

            val result = repo.getCurrentTravelModeSettings()
            assertNull(result)
        }
}

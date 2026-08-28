package io.pm.finlight.data.repository

import android.app.Application
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BackupSettingsRepository
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import io.pm.finlight.data.financeSettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BackupSettingsRepositoryTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var repository: BackupSettingsRepository

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        val dataStore = context.financeSettingsDataStore
        runTest {
            dataStore.edit { it.clear() }
        }
        repository = BackupSettingsRepository(dataStore)
    }

    @Test
    fun `save and get backup enabled`() =
        runTest {
            repository.getBackupEnabled().test {
                assertTrue(awaitItem()) // Default is true
                repository.saveBackupEnabled(false)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get auto backup enabled`() =
        runTest {
            repository.getAutoBackupEnabled().test {
                assertTrue(awaitItem()) // Default is true
                repository.saveAutoBackupEnabled(false)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get auto backup notification enabled`() =
        runTest {
            repository.getAutoBackupNotificationEnabled().test {
                assertFalse(awaitItem()) // Default is false
                repository.saveAutoBackupNotificationEnabled(true)
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get last backup timestamp`() =
        runTest {
            repository.getLastBackupTimestamp().test {
                assertEquals(0L, awaitItem()) // Default is 0L
                val time = 1700000000000L
                repository.saveLastBackupTimestamp(time)
                assertEquals(time, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `constructor with context initializes properly`() {
        val repo = BackupSettingsRepository(context)
        kotlin.test.assertNotNull(repo)
    }

    @Test
    fun `getters handle IOException by emitting defaults`() =
        runTest {
            val mockDataStore = io.mockk.mockk<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>()
            io.mockk.every { mockDataStore.data } returns kotlinx.coroutines.flow.flow { throw java.io.IOException("Disk error") }
            val repo = BackupSettingsRepository(mockDataStore)

            assertTrue(repo.getBackupEnabled().first())
            assertTrue(repo.getAutoBackupEnabled().first())
            assertFalse(repo.getAutoBackupNotificationEnabled().first())
            assertEquals(0L, repo.getLastBackupTimestamp().first())
        }

    @Test
    fun `getters rethrow non-IOException exceptions`() =
        runTest {
            val mockDataStore = io.mockk.mockk<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>()
            io.mockk.every { mockDataStore.data } returns kotlinx.coroutines.flow.flow { throw IllegalStateException("Fatal error") }
            val repo = BackupSettingsRepository(mockDataStore)

            kotlin.test.assertFailsWith<IllegalStateException> { repo.getBackupEnabled().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getAutoBackupEnabled().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getAutoBackupNotificationEnabled().first() }
            kotlin.test.assertFailsWith<IllegalStateException> { repo.getLastBackupTimestamp().first() }
        }
}

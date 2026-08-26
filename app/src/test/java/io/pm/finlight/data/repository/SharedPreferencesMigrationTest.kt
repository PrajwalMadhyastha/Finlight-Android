package io.pm.finlight.data.repository

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SharedPreferencesMigrationTest : BaseViewModelTest() {
    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `legacy shared preferences are migrated into DataStore properly`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val sharedPrefsName = "test_legacy_settings"
            val legacyPrefs = context.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE)

            // Populate legacy SharedPreferences
            legacyPrefs.edit()
                .putString("user_name", "LegacyMigratedUser")
                .putString("home_currency_code", "EUR")
                .putBoolean("google_drive_backup_enabled", false)
                .commit()

            val testFile = File(tmpFolder.newFolder(), "test_legacy_settings.preferences_pb")

            val dataStore: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(
                    migrations = listOf(SharedPreferencesMigration(context, sharedPrefsName)),
                    produceFile = { testFile },
                )

            val prefs = dataStore.data.first()

            val keyUserName = stringPreferencesKey("user_name")
            val keyCurrency = stringPreferencesKey("home_currency_code")
            val keyBackup = booleanPreferencesKey("google_drive_backup_enabled")

            assertEquals("LegacyMigratedUser", prefs[keyUserName])
            assertEquals("EUR", prefs[keyCurrency])
            assertFalse(prefs[keyBackup] ?: true)
        }
}

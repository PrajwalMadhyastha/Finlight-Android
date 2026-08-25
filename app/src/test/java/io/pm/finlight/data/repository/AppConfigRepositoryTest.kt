package io.pm.finlight.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.AppConfigRepository
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class AppConfigRepositoryTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var repository: AppConfigRepository
    private lateinit var prefs: SharedPreferences

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = AppConfigRepository(context)
    }

    @Test
    fun `save and get user name`() =
        runTest {
            val testName = "Jane Doe"
            repository.getUserName().test {
                assertEquals("User", awaitItem()) // Default
                repository.saveUserName(testName)
                assertEquals(testName, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get profile picture uri`() =
        runTest {
            val testUri = "content://pictures/1"
            repository.getProfilePictureUri().test {
                assertNull(awaitItem()) // Initial state is null
                repository.saveProfilePictureUri(testUri)
                assertEquals(testUri, awaitItem())
                repository.saveProfilePictureUri(null) // Test clearing
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get selected theme`() =
        runTest {
            val theme = AppTheme.AURORA
            repository.getSelectedTheme().test {
                assertEquals(AppTheme.SYSTEM_DEFAULT, awaitItem()) // Initial
                repository.saveSelectedTheme(theme)
                assertEquals(theme, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get home currency`() =
        runTest {
            val testCurrency = "USD"
            repository.getHomeCurrency().test {
                assertEquals("INR", awaitItem()) // Default
                repository.saveHomeCurrency(testCurrency)
                assertEquals(testCurrency, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `test constructor with shared preferences directly`() =
        runTest {
            val customRepo = AppConfigRepository(prefs)
            customRepo.saveUserName("DirectPrefsUser")
            customRepo.getUserName().test {
                assertEquals("DirectPrefsUser", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

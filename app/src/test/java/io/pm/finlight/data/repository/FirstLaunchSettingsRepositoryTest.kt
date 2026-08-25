package io.pm.finlight.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.FirstLaunchSettingsRepository
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var prefs: SharedPreferences
    private lateinit var internalPrefs: SharedPreferences

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        internalPrefs = context.getSharedPreferences("finlight_internal_state", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        internalPrefs.edit().clear().commit()
        repository = FirstLaunchSettingsRepository(context)
    }

    @Test
    fun `hasSeenOnboarding and setHasSeenOnboarding work correctly`() {
        assertFalse(repository.hasSeenOnboarding())
        repository.setHasSeenOnboarding(true)
        assertTrue(repository.hasSeenOnboarding())
        repository.setHasSeenOnboarding(false)
        assertFalse(repository.hasSeenOnboarding())
    }

    @Test
    fun `isFirstLaunchCompleteBlocking and setFirstLaunchComplete work correctly`() {
        assertFalse(repository.isFirstLaunchCompleteBlocking())
        repository.setFirstLaunchComplete()
        assertTrue(repository.isFirstLaunchCompleteBlocking())
    }
}

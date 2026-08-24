package io.pm.finlight.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.SmsRuleSettingsRepository
import io.pm.finlight.TestApplication
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
class SmsRuleSettingsRepositoryTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var repository: SmsRuleSettingsRepository
    private lateinit var prefs: SharedPreferences

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("finance_app_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = SmsRuleSettingsRepository(context)
    }

    @Test
    fun `save and get sms scan start date`() =
        runTest {
            val date = 1690000000000L
            repository.getSmsScanStartDate().test {
                awaitItem() // Default 30 days ago
                repository.saveSmsScanStartDate(date)
                assertEquals(date, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save and get ignore rules checksum`() {
        assertEquals(0, repository.getIgnoreRulesChecksum())
        repository.saveIgnoreRulesChecksum(12345)
        assertEquals(12345, repository.getIgnoreRulesChecksum())
    }

    @Test
    fun `add and get dismissed merge suggestions`() =
        runTest {
            repository.getDismissedMergeSuggestions().test {
                assertEquals(emptySet(), awaitItem()) // Default
                repository.addDismissedMergeSuggestion("suggestion_1")
                assertEquals(setOf("suggestion_1"), awaitItem())
                repository.addDismissedMergeSuggestion("suggestion_2")
                assertEquals(setOf("suggestion_1", "suggestion_2"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

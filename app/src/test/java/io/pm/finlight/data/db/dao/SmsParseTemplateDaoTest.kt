package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.SmsParseTemplate
import io.pm.finlight.SmsParseTemplateDao
import io.pm.finlight.TestApplication
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsParseTemplateDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var smsParseTemplateDao: SmsParseTemplateDao

    @Before
    fun setup() {
        smsParseTemplateDao = dbRule.db.smsParseTemplateDao()
    }

    @Test
    fun `inserting template with same signature is ignored`() = runTest {
        // Arrange
        val template1 = SmsParseTemplate(templateSignature = "sig1", originalSmsBody = "body1", originalMerchantStartIndex = 0, originalMerchantEndIndex = 1, originalAmountStartIndex = 2, originalAmountEndIndex = 3)
        val template2 = SmsParseTemplate(templateSignature = "sig1", originalSmsBody = "body2", originalMerchantStartIndex = 0, originalMerchantEndIndex = 1, originalAmountStartIndex = 2, originalAmountEndIndex = 3) // Same signature

        // Act
        smsParseTemplateDao.insert(template1)
        smsParseTemplateDao.insert(template2) // This should be ignored

        // Assert
        val allTemplates = smsParseTemplateDao.getAllTemplates()
        assertEquals(1, allTemplates.size)
        assertEquals("body1", allTemplates.first().originalSmsBody)
    }

    @Test
    fun `insertAll and deleteAll work correctly`() = runTest {
        // Arrange
        val templates = listOf(
            SmsParseTemplate(templateSignature = "sig1", originalSmsBody = "body1", originalMerchantStartIndex = 0, originalMerchantEndIndex = 1, originalAmountStartIndex = 2, originalAmountEndIndex = 3),
            SmsParseTemplate(templateSignature = "sig2", originalSmsBody = "body2", originalMerchantStartIndex = 0, originalMerchantEndIndex = 1, originalAmountStartIndex = 2, originalAmountEndIndex = 3)
        )

        // Act (insertAll)
        smsParseTemplateDao.insertAll(templates)

        // Assert (insertAll)
        var allTemplates = smsParseTemplateDao.getAllTemplates()
        assertEquals(2, allTemplates.size)

        // Act (deleteAll)
        smsParseTemplateDao.deleteAll()

        // Assert (deleteAll)
        allTemplates = smsParseTemplateDao.getAllTemplates()
        assertTrue(allTemplates.isEmpty())
    }
}


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

    private val template1 = SmsParseTemplate(
        templateSignature = "sig1",
        correctedMerchantName = "Amazon",
        originalSmsBody = "body1",
        originalMerchantStartIndex = 0,
        originalMerchantEndIndex = 1,
        originalAmountStartIndex = 2,
        originalAmountEndIndex = 3
    )
    private val template2 = SmsParseTemplate(
        templateSignature = "sig1",
        correctedMerchantName = "Flipkart",
        originalSmsBody = "body2",
        originalMerchantStartIndex = 0,
        originalMerchantEndIndex = 1,
        originalAmountStartIndex = 2,
        originalAmountEndIndex = 3
    )
    private val template3 = SmsParseTemplate(
        templateSignature = "sig2",
        correctedMerchantName = "Amazon",
        originalSmsBody = "body3",
        originalMerchantStartIndex = 0,
        originalMerchantEndIndex = 1,
        originalAmountStartIndex = 2,
        originalAmountEndIndex = 3
    )

    @Test
    fun `inserting template with same composite primary key is ignored`() = runTest {
        // Arrange
        val template1_duplicate = SmsParseTemplate(
            templateSignature = "sig1",
            correctedMerchantName = "Amazon", // Same composite PK as template1
            originalSmsBody = "body_duplicate",
            originalMerchantStartIndex = 10,
            originalMerchantEndIndex = 11,
            originalAmountStartIndex = 12,
            originalAmountEndIndex = 13
        )

        // Act
        smsParseTemplateDao.insert(template1)
        smsParseTemplateDao.insert(template1_duplicate) // This should be ignored

        // Assert
        val allTemplates = smsParseTemplateDao.getAllTemplates()
        assertEquals(1, allTemplates.size)
        assertEquals("body1", allTemplates.first().originalSmsBody)
    }

    @Test
    fun `inserting template with different composite key is successful`() = runTest {
        // Arrange
        // template1 and template2 have the same signature but different merchant names

        // Act
        smsParseTemplateDao.insert(template1)
        smsParseTemplateDao.insert(template2)

        // Assert
        val allTemplates = smsParseTemplateDao.getAllTemplates()
        assertEquals(2, allTemplates.size)
    }

    @Test
    fun `getAllTemplates returns all templates`() = runTest {
        // Arrange
        val templates = listOf(template1, template2, template3)
        smsParseTemplateDao.insertAll(templates)

        // Act
        val allTemplates = smsParseTemplateDao.getAllTemplates()

        // Assert
        assertEquals(3, allTemplates.size)
        assertTrue(allTemplates.any { it.templateSignature == "sig1" && it.correctedMerchantName == "Amazon" })
        assertTrue(allTemplates.any { it.templateSignature == "sig1" && it.correctedMerchantName == "Flipkart" })
        assertTrue(allTemplates.any { it.templateSignature == "sig2" && it.correctedMerchantName == "Amazon" })
    }

    @Test
    fun `getTemplatesBySignature returns only matching templates`() = runTest {
        // Arrange
        val templates = listOf(template1, template2, template3)
        smsParseTemplateDao.insertAll(templates)

        // Act
        val sig1Templates = smsParseTemplateDao.getTemplatesBySignature("sig1")
        val sig2Templates = smsParseTemplateDao.getTemplatesBySignature("sig2")
        val sig3Templates = smsParseTemplateDao.getTemplatesBySignature("sig3")

        // Assert
        assertEquals(2, sig1Templates.size)
        assertTrue(sig1Templates.all { it.templateSignature == "sig1" })
        assertEquals(1, sig2Templates.size)
        assertTrue(sig2Templates.all { it.templateSignature == "sig2" })
        assertTrue(sig3Templates.isEmpty())
    }

    @Test
    fun `getTemplatesBySignature is case insensitive`() = runTest {
        // Arrange
        val templates = listOf(template1, template2, template3)
        smsParseTemplateDao.insertAll(templates)

        // Act
        val sig1Templates = smsParseTemplateDao.getTemplatesBySignature("SIG1") // Query with uppercase

        // Assert
        assertEquals(2, sig1Templates.size)
        assertTrue(sig1Templates.all { it.templateSignature == "sig1" })
    }

    @Test
    fun `insertAll and deleteAll work correctly`() = runTest {
        // Arrange
        val templates = listOf(template1, template2)

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

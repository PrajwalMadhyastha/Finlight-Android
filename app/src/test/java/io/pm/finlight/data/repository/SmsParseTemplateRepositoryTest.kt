package io.pm.finlight.data.repository

import io.pm.finlight.SmsParseTemplate
import io.pm.finlight.SmsParseTemplateDao
import io.pm.finlight.SmsParseTemplateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
class SmsParseTemplateRepositoryTest {
    @Mock
    private lateinit var smsParseTemplateDao: SmsParseTemplateDao

    private lateinit var repository: SmsParseTemplateRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = SmsParseTemplateRepository(smsParseTemplateDao)
    }

    @Test
    fun `insert delegates to DAO`() =
        runTest {
            val template =
                SmsParseTemplate(
                    templateSignature = "sig",
                    correctedMerchantName = "Merchant",
                    originalSmsBody = "body",
                    originalMerchantStartIndex = 0,
                    originalMerchantEndIndex = 8,
                    originalAmountStartIndex = 10,
                    originalAmountEndIndex = 15,
                )
            repository.insert(template)
            verify(smsParseTemplateDao).insert(template)
        }

    @Test
    fun `getAllTemplates delegates to DAO`() =
        runTest {
            val templates =
                listOf(
                    SmsParseTemplate(
                        templateSignature = "sig",
                        correctedMerchantName = "Merchant",
                        originalSmsBody = "body",
                        originalMerchantStartIndex = 0,
                        originalMerchantEndIndex = 8,
                        originalAmountStartIndex = 10,
                        originalAmountEndIndex = 15,
                    ),
                )
            `when`(smsParseTemplateDao.getAllTemplates()).thenReturn(templates)

            val result = repository.getAllTemplates()
            assertEquals(templates, result)
            verify(smsParseTemplateDao).getAllTemplates()
        }

    @Test
    fun `getTemplatesBySignature delegates to DAO`() =
        runTest {
            val templates =
                listOf(
                    SmsParseTemplate(
                        templateSignature = "sig",
                        correctedMerchantName = "Merchant",
                        originalSmsBody = "body",
                        originalMerchantStartIndex = 0,
                        originalMerchantEndIndex = 8,
                        originalAmountStartIndex = 10,
                        originalAmountEndIndex = 15,
                    ),
                )
            `when`(smsParseTemplateDao.getTemplatesBySignature("sig")).thenReturn(templates)

            val result = repository.getTemplatesBySignature("sig")
            assertEquals(templates, result)
            verify(smsParseTemplateDao).getTemplatesBySignature("sig")
        }

    @Test
    fun `insertAll delegates to DAO`() =
        runTest {
            val templates =
                listOf(
                    SmsParseTemplate(
                        templateSignature = "sig",
                        correctedMerchantName = "Merchant",
                        originalSmsBody = "body",
                        originalMerchantStartIndex = 0,
                        originalMerchantEndIndex = 8,
                        originalAmountStartIndex = 10,
                        originalAmountEndIndex = 15,
                    ),
                )
            repository.insertAll(templates)
            verify(smsParseTemplateDao).insertAll(templates)
        }

    @Test
    fun `deleteAll delegates to DAO`() =
        runTest {
            repository.deleteAll()
            verify(smsParseTemplateDao).deleteAll()
        }
}

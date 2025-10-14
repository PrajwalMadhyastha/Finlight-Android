// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/core/parser/ParserLearningLogicTest.kt
// REASON: NEW FILE - This file adds critical regression tests for the SmsParser's
// "learning" logic. It verifies that category mappings are checked before
// merchant names are renamed, and that rename rules are applied correctly. This
// ensures the "Hierarchy of Trust" for transaction enrichment is maintained.
// =================================================================================
package io.pm.finlight.core.parser

import io.pm.finlight.MerchantCategoryMapping
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class ParserLearningLogicTest : BaseSmsParserTest() {

    @Before
    fun initializeMocks() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `test_categoryLearning_usesOriginalMerchantName`() = runBlocking {
        // ARRANGE
        // The parser finds "AMZN" but the user has taught it that "Amazon" maps to category 99.
        val originalMerchant = "AMZN"
        val expectedCategoryId = 99
        val smsBody = "You spent Rs. 500 at $originalMerchant on 01-Jan-25."
        val mockSms = SmsMessage(1L, "TestSender", smsBody, System.currentTimeMillis())

        setupTest()
        // Mock the category mapping DAO to return our test category ID for the original name.
        Mockito.`when`(mockMerchantCategoryMappingDao.getCategoryIdForMerchant(originalMerchant))
            .thenReturn(expectedCategoryId)

        // ACT
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        // ASSERT
        assertNotNull("Parser should have produced a result", result)
        // Verify that the category mapping was checked using the ORIGINAL merchant name.
        Mockito.verify(mockMerchantCategoryMappingDao).getCategoryIdForMerchant(originalMerchant)
        // Verify that the final transaction has the correct, learned category ID.
        assertEquals(expectedCategoryId, result?.categoryId)
    }

    @Test
    fun `test_renameRule_isAppliedToFinalMerchantName`() = runBlocking {
        // ARRANGE
        // The parser finds "AMZN", but the user has created a rule to rename it to "Amazon".
        val originalMerchant = "AMZN"
        val expectedFinalMerchant = "Amazon"
        val smsBody = "You spent Rs. 500 at $originalMerchant on 01-Jan-25."
        val mockSms = SmsMessage(1L, "TestSender", smsBody, System.currentTimeMillis())

        val renameRules = listOf(MerchantRenameRule(originalName = originalMerchant, newName = expectedFinalMerchant))
        setupTest(renameRules = renameRules)

        // ACT
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        // ASSERT
        assertNotNull("Parser should have produced a result", result)
        // Verify that the final merchant name in the transaction object is the RENAMED one.
        assertEquals(expectedFinalMerchant, result?.merchantName)
    }

    // =================================================================================
    // REGRESSION TEST FOR HIERARCHY OF TRUST BUG
    // This test specifically verifies the fix for the merchant category learning bug.
    // It ensures that the parser attempts to find a learned category using the
    // ORIGINAL merchant name ("AMZN") *before* it applies any rename rules
    // (which would change it to "Amazon").
    // =================================================================================
    @Test
    fun `test_combinedLogic_learnsCategoryBeforeApplyingRename`() = runBlocking {
        // ARRANGE
        // The parser finds "AMZN". The user has taught it:
        // 1. That "AMZN" belongs to the "Shopping" category (ID 99).
        // 2. That "AMZN" should be renamed to "Amazon" for display.
        val originalMerchant = "AMZN"
        val expectedFinalMerchant = "Amazon"
        val expectedCategoryId = 99
        val smsBody = "You spent Rs. 500 at $originalMerchant on 01-Jan-25."
        val mockSms = SmsMessage(1L, "TestSender", smsBody, System.currentTimeMillis())

        val renameRules = listOf(MerchantRenameRule(originalName = originalMerchant, newName = expectedFinalMerchant))
        setupTest(renameRules = renameRules)

        // Mock the category DAO to return the learned category for the ORIGINAL name.
        Mockito.`when`(mockMerchantCategoryMappingDao.getCategoryIdForMerchant(originalMerchant))
            .thenReturn(expectedCategoryId)

        // Create an "inOrder" verifier to check the sequence of DAO calls.
        val inOrder = Mockito.inOrder(mockMerchantCategoryMappingDao, mockMerchantRenameRuleDao)

        // ACT
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        // ASSERT
        assertNotNull("Parser should have produced a result", result)
        // Verify the order of operations: Category learning must happen BEFORE renaming.
        // This confirms the parser checks for a category using the raw, original merchant name first.
        inOrder.verify(mockMerchantCategoryMappingDao).getCategoryIdForMerchant(originalMerchant)

        // Finally, assert that the resulting transaction has BOTH the correct learned category AND the renamed merchant.
        assertEquals(expectedCategoryId, result?.categoryId)
        assertEquals(expectedFinalMerchant, result?.merchantName)
    }
}
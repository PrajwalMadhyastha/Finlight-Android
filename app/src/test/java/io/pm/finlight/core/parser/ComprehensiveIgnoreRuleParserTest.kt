// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/core/parser/ComprehensiveIgnoreRuleParserTest.kt
// REASON: NEW FILE - This file contains a comprehensive, parameterized unit test
// that validates every single default ignore rule in the system. This ensures
// that the core SMS parser correctly filters out known non-transactional and
// promotional messages, providing a robust safety net against regressions.
// FIX - Added a @Before method with MockitoAnnotations.openMocks(this) to
// correctly initialize the lateinit mock DAOs from the base class, resolving
// an UninitializedPropertyAccessException.
// =================================================================================
package io.pm.finlight.core.parser

import io.pm.finlight.DEFAULT_IGNORE_PHRASES
import io.pm.finlight.IgnoreRule
import io.pm.finlight.RuleType
import io.pm.finlight.SmsMessage
import io.pm.finlight.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.MockitoAnnotations

/**
 * A parameterized test suite that iterates through every default ignore rule
 * and verifies that the SmsParser correctly ignores messages that match.
 */
@RunWith(Parameterized::class)
class ComprehensiveIgnoreRuleParserTest(
    private val ruleToTest: IgnoreRule
) : BaseSmsParserTest() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: Rule({0})")
        fun data(): Collection<Array<Any>> {
            // Create a collection of arrays, where each array contains the parameters for one test run.
            // In this case, it's just the IgnoreRule object.
            return DEFAULT_IGNORE_PHRASES.map { arrayOf(it as Any) }
        }
    }

    /**
     * Initializes Mockito mocks before each test run. This is necessary because
     * the Parameterized runner does not do this automatically.
     */
    @Before
    fun initializeMocks() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `test_defaultIgnoreRule_isCorrectlyApplied`() = runBlocking {
        // ARRANGE
        // Set up the parser with only the specific rule we want to test.
        setupTest(ignoreRules = listOf(ruleToTest))

        val mockSms: SmsMessage = when (ruleToTest.type) {
            RuleType.SENDER -> {
                // For sender rules, create a sender that matches the wildcard pattern
                // and a body that looks like a transaction to ensure the sender rule is what's causing the ignore.
                val mockSender = ruleToTest.pattern.replace("*", "TEST")
                SmsMessage(
                    id = 1L,
                    sender = mockSender,
                    body = "You spent Rs. 100 on something that should be ignored.",
                    date = System.currentTimeMillis()
                )
            }
            RuleType.BODY_PHRASE -> {
                // For body rules, use a generic sender and embed the ignore phrase in the body.
                SmsMessage(
                    id = 1L,
                    sender = "VM-GENERIC",
                    body = "This is a test message about ${ruleToTest.pattern} for Rs. 500.",
                    date = System.currentTimeMillis()
                )
            }
        }

        // ACT
        val result = SmsParser.parse(mockSms, emptyMappings, customSmsRuleProvider, merchantRenameRuleProvider, ignoreRuleProvider, merchantCategoryMappingProvider, categoryFinderProvider, smsParseTemplateProvider)

        // ASSERT
        assertNull(
            "Parser should have ignored the message based on the rule: '${ruleToTest.pattern}' (Type: ${ruleToTest.type})",
            result
        )
    }
}
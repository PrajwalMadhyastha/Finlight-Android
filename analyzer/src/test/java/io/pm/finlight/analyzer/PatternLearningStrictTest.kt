package io.pm.finlight.analyzer

import org.junit.Assert.*
import org.junit.Test

class PatternLearningStrictTest {

    private val learner = PatternLearner()

    @Test
    fun testStrictTemplateLearningAndApplication() {
        val item1 = NerLabelItem(
            id = "1",
            sender = "HDFC",
            body = "Rs 5000 credited to Acct 123",
            timestamp = 1000L,
            tokens = listOf("Rs", "5000", "credited", "to", "Acct", "123"),
            labels = NerLabels(
                amountTokenIndices = listOf(1), // "5000"
                accountTokenIndices = listOf(5) // "123"
            ),
            parserSuggestions = ParserSuggestions()
        )

        var library = PatternLibrary()
        library = learner.learnFromLabels(item1, library)

        assertEquals("Should have learned exactly 1 template pattern", 1, library.templatePatterns.size)
        // Check fuzzy match on template string since <NUM> logic might vary slightly on regex
        assertTrue("Template should contain <NUM>", library.templatePatterns[0].templateString.contains("<NUM>"))

        val item2Tokens = listOf("Rs", "9999", "credited", "to", "Acct", "456")
        val match = learner.findMatchingPatterns(item2Tokens, library)

        assertNotNull("Should find an amount match via template", match.second) 
        assertEquals(listOf(1), match.second)
        
        assertNotNull("Should find an account match via template", match.third) 
        assertEquals(listOf(5), match.third)
    }
    @Test
    fun testHdfcMultilineFailure() {
        // User's example:
        // Sent Rs.180.00
        // From HDFC Bank A/C x1234
        // To P PADMANABHA
        // ...
        
        // 1. Learn from Item 1
        val tokens1 = listOf(
            "Sent", "Rs.180.00", // "Rs.180.00" as one token (simulated)
            "From", "HDFC", "Bank", "A/C", "x1234", 
            "To", "P", "PADMANABHA", // Payee Name (Unlabeled)
            "On", "25/12/24", 
            "Ref", "436012345143", 
            "Not", "You?", 
            "Call", "18002586161/SMS", "BLOCK", "UPI", "to", "7308080808"
        )
        
        val item1 = NerLabelItem(
            id = "1", sender = "HDFC", body = "multiline...", timestamp = 0L,
            tokens = tokens1,
            labels = NerLabels(
                amountTokenIndices = listOf(1), // "Rs.180.00"
                accountTokenIndices = listOf(3, 4, 5, 6) // "HDFC Bank A/C x1234"
            ),
            parserSuggestions = ParserSuggestions()
        )

        var library = PatternLibrary()
        library = learner.learnFromLabels(item1, library)
        
        val learnedTemplate = library.templatePatterns.firstOrNull()?.templateString
        println("Learned Template: $learnedTemplate")
        
        // 2. Test Item 2 - Different Payee
        // If the template included "p padmanabha", it will fail here
        val tokens2 = listOf(
             "Sent", "Rs.200.00", 
             "From", "HDFC", "Bank", "A/C", "x1234", 
             "To", "SOMEONE", "ELSE", // Changed Payee
             "On", "26/12/24", 
             "Ref", "999999999999", 
             "Not", "You?", 
             "Call", "18002586161/SMS", "BLOCK", "UPI", "to", "7308080808"
        )

        val match = learner.findMatchingPatterns(tokens2, library)

        // ASSERTION: With Universal Word Masking (<WORD>), this SHOULD MATCH!
        // "To P PADMANABHA" -> "to <WORD> <WORD>"
        // "To SOMEONE ELSE" -> "to <WORD> <WORD>"
        // Structure is identical.
        
        assertNotNull("Template SHOULD match even if payee name changes (masked as <WORD>)", match.second)
        assertEquals("Amount should be correctly extracted", listOf(1), match.second)
    }

    @Test
    fun testBankOfBarodaTemplateFailure() {
        // User's example:
        // "Rs.100 Credited ... by harshitha.rajan. Total Bal:..."
        
        // 1. Learn from Item 1 ("harshitha.rajan")
        // Tokenization: "harshitha", ".", "rajan" (3 tokens) vs "john" (1 token)
        // If "harshitha" and "rajan" become <WORD>, "." remains "."
        // Template: "... by <WORD> . <WORD> ..."
        
        val tokens1 = listOf(
            "Rs.100", "Credited", "to", "A/c", "...1234", 
            "thru", "UPI/270312345389", 
            "by", "harshitha", ".", "rajan", ".", // 3 tokens for name + period 
            "Total", "Bal", ":", "Rs.963.9CR"
        )
        
        val item1 = NerLabelItem(
            id = "1", sender = "BOB", body = "...", timestamp = 0L,
            tokens = tokens1,
            labels = NerLabels(
                amountTokenIndices = listOf(0), // "Rs.100"
                accountTokenIndices = listOf(4) // "...1234"
            ),
            parserSuggestions = ParserSuggestions()
        )

        var library = PatternLibrary()
        library = learner.learnFromLabels(item1, library)
        
        println("Learned Template: " + library.templatePatterns.firstOrNull()?.templateString)
        
        // 2. Test Item 2 ("john.doe") -> same structure
        // "john", ".", "doe" -> Should match
        val tokens2 = listOf(
            "Rs.200", "Credited", "to", "A/c", "...1234", 
            "thru", "UPI/999999999999", 
            "by", "john", ".", "doe", ".", 
            "Total", "Bal", ":", "Rs.1200.0CR"
        )
        val match2 = learner.findMatchingPatterns(tokens2, library)
        assertNotNull("Should match same structure (john.doe)", match2.second)
        
        // 3. Test Item 3 ("senthil") -> simpler structure
        // "senthil" -> 1 token
        // Template expects <WORD> . <WORD>
        // Actual is <WORD>
        // Mismatch!
        
        val tokens3 = listOf(
            "Rs.500", "Credited", "to", "A/c", "...1234", 
            "thru", "UPI/888888888888", 
            "by", "senthil", ".", // Name is just senthil + period
            "Total", "Bal", ":", "Rs.500.0CR"
        )
        
        val match3 = learner.findMatchingPatterns(tokens3, library)
        
        // Assertion: Now expected to SUCCEED with Fuzzy Template Matching
        // "senthil" (1 token) aligns with "harshitha.rajan" (3 tokens) as a substitution/deletion block
        // The rest of the template aligns perfectly.
        
        if (match3.second != null) {
            println("Verified: Fuzzy Template Match succeeded!")
        } else {
             println("Failed: Fuzzy Template Match didn't find good alignment.")
        }
        
        assertNotNull("Fuzzy Template should match for 'senthil' vs 'harshitha.rajan'", match3.second)
        assertEquals("Amount should be correctly extracted", listOf(0), match3.second)
    }
}

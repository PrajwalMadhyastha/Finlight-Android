package io.pm.finlight.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class StringSimilarityTest {
    @Test
    fun `test exact match`() {
        val score = StringSimilarity.calculateTokenOverlapScore("amazon", "amazon")
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `test legacy prefix suffix`() {
        val legacy = "ACH*ZERODHA B"
        val ner = "zerodha b"
        val score = StringSimilarity.calculateTokenOverlapScore(legacy, ner)
        assertEquals(1.0, score, 0.001) // Intersection is [zerodha, b] (2), minSize is 2
    }

    @Test
    fun `test legacy upi artifacts`() {
        val legacy = "UPI-AMAZON-merchant@paytm"
        val ner = "amazon merchant"
        val score = StringSimilarity.calculateTokenOverlapScore(legacy, ner)
        assertEquals(1.0, score, 0.001) // Intersection is [amazon, merchant] (2), minSize is 2
    }

    @Test
    fun `test partial intersection`() {
        val legacy = "HDFC Bank Card"
        val ner = "hdfc credit card"
        val score = StringSimilarity.calculateTokenOverlapScore(legacy, ner)
        // Tokens legacy: [hdfc, bank, card]
        // Tokens ner: [hdfc, credit, card]
        // Intersection: [hdfc, card] (2). minSize: 3
        assertEquals(2.0 / 3.0, score, 0.001)
    }

    @Test
    fun `test zero overlap`() {
        val legacy = "MC DONALDS"
        val ner = "starbucks"
        val score = StringSimilarity.calculateTokenOverlapScore(legacy, ner)
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun `test null and blank`() {
        assertEquals(0.0, StringSimilarity.calculateTokenOverlapScore(null, "foo"), 0.001)
        assertEquals(0.0, StringSimilarity.calculateTokenOverlapScore("", "foo"), 0.001)
        assertEquals(0.0, StringSimilarity.calculateTokenOverlapScore("   ", "foo"), 0.001)
    }

    @Test
    fun `test jaccard index`() {
        val legacy1 = "Amazon Pay"
        val legacy2 = "Amazon.in"
        val ner = "Amazon"

        val score1 = StringSimilarity.calculateJaccardIndex(legacy1, ner)
        val score2 = StringSimilarity.calculateJaccardIndex(legacy2, ner)

        // Tokens: [amazon, pay] U [amazon] -> size 2. Intersection -> 1. Jaccard = 1/2 = 0.5
        assertEquals(0.5, score1, 0.001)
        assertEquals(0.5, score2, 0.001)
    }

    // =========================================================================
    // --- isCanonicalSubset Tests ---
    // =========================================================================

    @Test
    fun `isCanonicalSubset returns true when canonical tokens are a subset of incoming tokens`() {
        // "Swiggy" (1 token) is present inside "SWIGGY INDIA PVT LTD" after cleaning
        assert(StringSimilarity.isCanonicalSubset("Swiggy", "SWIGGY INDIA"))
    }

    @Test
    fun `isCanonicalSubset returns true for multi-token canonical match`() {
        // "Amazon Pay" (2 tokens) should match "AMAZON PAY MERCHANT"
        assert(StringSimilarity.isCanonicalSubset("Amazon Pay", "AMAZON PAY MERCHANT"))
    }

    @Test
    fun `isCanonicalSubset returns false when canonical token is missing from incoming`() {
        // "Swiggy" is NOT in "ZOMATO INDIA"
        assert(!StringSimilarity.isCanonicalSubset("Swiggy", "ZOMATO INDIA"))
    }

    @Test
    fun `isCanonicalSubset returns false when canonical name is shorter than 5 chars`() {
        // "Pay" (3 chars) must not match anything — too short, too risky
        assert(!StringSimilarity.isCanonicalSubset("Pay", "PAYTM INDIA"))
        // "SBI" (3 chars) must not match
        assert(!StringSimilarity.isCanonicalSubset("SBI", "SBI BANK CARD"))
    }

    @Test
    fun `isCanonicalSubset returns false for null or blank inputs`() {
        assert(!StringSimilarity.isCanonicalSubset(null, "SWIGGY INDIA"))
        assert(!StringSimilarity.isCanonicalSubset("Swiggy", null))
        assert(!StringSimilarity.isCanonicalSubset("", "SWIGGY INDIA"))
        assert(!StringSimilarity.isCanonicalSubset("Swiggy", ""))
    }

    @Test
    fun `isCanonicalSubset is case-insensitive`() {
        assert(StringSimilarity.isCanonicalSubset("swiggy", "SWIGGY INFOTECH"))
        assert(StringSimilarity.isCanonicalSubset("SWIGGY", "swiggy india"))
    }

    @Test
    fun `isCanonicalSubset returns false when only partial canonical tokens match`() {
        // "Amazon Pay" has tokens [amazon, pay]. "AMAZON MERCHANT" only has [amazon, merchant].
        // "pay" is missing, so should NOT match.
        assert(!StringSimilarity.isCanonicalSubset("Amazon Pay", "AMAZON MERCHANT"))
    }
}

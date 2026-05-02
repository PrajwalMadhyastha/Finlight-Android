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
}

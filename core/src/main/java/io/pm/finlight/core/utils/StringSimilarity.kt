package io.pm.finlight.core.utils

import kotlin.math.min

object StringSimilarity {

    /**
     * Calculates a token overlap similarity score between [str1] and [str2].
     * Returns 1.0 if one string's tokens are a pure subset of the other's.
     * Useful for comparing extracted NER merchants against legacy regex merchants
     * (e.g. "zerodha b" vs "ACH*ZERODHA B" -> 1.0).
     */
    fun calculateTokenOverlapScore(str1: String?, str2: String?): Double {
        if (str1.isNullOrBlank() || str2.isNullOrBlank()) return 0.0

        val tokens1 = str1.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()
        val tokens2 = str2.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        val intersectionSize = tokens1.intersect(tokens2).size
        val minSize = min(tokens1.size, tokens2.size)

        return intersectionSize.toDouble() / minSize
    }

    /**
     * Calculates the Jaccard similarity index (Intersection over Union).
     * Useful as a tie-breaker when multiple strings achieve an identical overlap score.
     */
    fun calculateJaccardIndex(str1: String?, str2: String?): Double {
        if (str1.isNullOrBlank() || str2.isNullOrBlank()) return 0.0

        val tokens1 = str1.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()
        val tokens2 = str2.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()

        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        val intersectionSize = tokens1.intersect(tokens2).size
        val unionSize = (tokens1 + tokens2).size

        return intersectionSize.toDouble() / unionSize
    }
}

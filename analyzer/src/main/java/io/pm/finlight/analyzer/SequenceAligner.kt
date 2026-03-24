package io.pm.finlight.analyzer

import kotlin.math.max

object SequenceAligner {

    /**
     * Represents a mapping from Source Index -> Target Index
     */
    data class AlignmentResult(
        val score: Double,
        val mapping: Map<Int, Int> // Template Index -> Input Index
    )

    /**
     * Aligns template tokens with input tokens using a simplified LCS/Edit Distance approach.
     * Returns a mapping of indices from template to input if alignment is good enough.
     */
    fun align(template: List<String>, input: List<String>): AlignmentResult {
        // Dynamic Programming Table for Longest Common Subsequence of *Matching Tokens*
        // We use LCS because we want to find the longest sequence of matching tokens
        // preserving relative order.
        
        val m = template.size
        val n = input.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                if (tokensMatch(template[i - 1], input[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        // Backtrack to find the mapping
        val mapping = mutableMapOf<Int, Int>()
        var i = m
        var j = n
        var matches = 0

        while (i > 0 && j > 0) {
            if (tokensMatch(template[i - 1], input[j - 1])) {
                // Determine if this match is part of the optimal path
                // In standard LCS backtracking: if chars match, we came from diagonal.
                // But we need to be careful if multiple paths exist.
                // Standard: if match, take diagonal.
                if (dp[i][j] == dp[i-1][j-1] + 1) { // It was a match used in LCS
                    mapping[i - 1] = j - 1
                    matches++
                    i--
                    j--
                } else {
                    // Ambiguous case or not part of LCS?
                    // If match but not derived from diag? 
                    // Actually if tokens match, dp logic usually prioritizes diag?
                    // No, if max(top, left) > diag+1 (impossible), then take max.
                    // If max(top, left) == diag+1, we can take diag.
                    // Let's stick to standard backtracking
                     if (dp[i - 1][j] > dp[i][j - 1]) {
                        i--
                    } else {
                        j--
                    }
                }
            } else {
                if (dp[i - 1][j] > dp[i][j - 1]) {
                    i--
                } else {
                    j--
                }
            }
        }
        
        // Calculate Score: % of template matched
        // If template has 10 tokens and we matched 8, score is 0.8.
        val score = if (m > 0) matches.toDouble() / m else 0.0
        
        return AlignmentResult(score, mapping)
    }

    private fun tokensMatch(t1: String, t2: String): Boolean {
        // Strict equality for now, but could be looser
        // Since we already masked tokens in `extractTemplate`,
        // <WORD> == <WORD>, <NUM> == <NUM>, etc.
        return t1 == t2
    }
}

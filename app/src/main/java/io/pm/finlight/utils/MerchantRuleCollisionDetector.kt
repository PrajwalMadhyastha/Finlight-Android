package io.pm.finlight.utils

import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.core.utils.StringSimilarity

object MerchantRuleCollisionDetector {
    private val GENERIC_STOPWORDS =
        setOf(
            "pvt",
            "ltd",
            "limited",
            "inc",
            "co",
            "corp",
            "services",
            "service",
            "india",
            "in",
            "the",
            "and",
            "pay",
            "payment",
            "payments",
            "pos",
            "upi",
            "card",
            "bank",
            "app",
            "store",
            "shop",
            "online",
            "retail",
            "billdesk",
            "razorpay",
            "tech",
            "technologies",
            "solutions",
        )

    /**
     * Identifies collisions and potential shadowing between merchant rename rules.
     * Returns a map of lowercase originalName -> list of conflicting rules.
     */
    fun findCollisions(rules: List<MerchantRenameRule>): Map<String, List<MerchantRenameRule>> {
        val result = mutableMapOf<String, MutableList<MerchantRenameRule>>()

        for (i in rules.indices) {
            val ruleA = rules[i]
            for (j in i + 1 until rules.size) {
                val ruleB = rules[j]

                if (areConflicting(ruleA, ruleB)) {
                    result.getOrPut(ruleA.originalName.lowercase()) { mutableListOf() }.add(ruleB)
                    result.getOrPut(ruleB.originalName.lowercase()) { mutableListOf() }.add(ruleA)
                }
            }
        }

        return result
    }

    /**
     * Checks if two rules have overlapping token roots or fuzzy match shadowing
     * with different rename targets.
     */
    fun areConflicting(
        ruleA: MerchantRenameRule,
        ruleB: MerchantRenameRule,
    ): Boolean {
        if (ruleA.originalName.equals(ruleB.originalName, ignoreCase = true)) {
            return false // Same rule
        }

        // If target names are identical, there is no collision/shadowing ambiguity
        if (ruleA.newName.trim().equals(ruleB.newName.trim(), ignoreCase = true)) {
            return false
        }

        val tokensA = extractTokens(ruleA.originalName)
        val tokensB = extractTokens(ruleB.originalName)

        if (tokensA.isEmpty() || tokensB.isEmpty()) return false

        // 1. High token overlap score (>= 0.85)
        val overlapScore = StringSimilarity.calculateTokenOverlapScore(ruleA.originalName, ruleB.originalName)
        if (overlapScore >= 0.85) return true

        // 2. Token subset containment
        if (tokensA.all { it in tokensB } || tokensB.all { it in tokensA }) {
            return true
        }

        // 3. Significant non-stopword token intersection (root sharing)
        val significantA = tokensA.filter { it.length >= 3 && it !in GENERIC_STOPWORDS }.toSet()
        val significantB = tokensB.filter { it.length >= 3 && it !in GENERIC_STOPWORDS }.toSet()
        val sharedSignificant = significantA.intersect(significantB)

        if (sharedSignificant.isNotEmpty()) {
            return true
        }

        // 4. Reverse canonical overlap (rule.newName token subset of other's originalName)
        if (StringSimilarity.isCanonicalSubset(ruleA.newName, ruleB.originalName) ||
            StringSimilarity.isCanonicalSubset(ruleB.newName, ruleA.originalName)
        ) {
            return true
        }

        return false
    }

    private fun extractTokens(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .toSet()
    }
}

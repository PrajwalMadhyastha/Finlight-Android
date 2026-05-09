package io.pm.finlight.utils

import io.pm.finlight.TransactionDetails
import java.util.Locale

/**
 * Helper object to centralize merchant alias application logic.
 */
object MerchantAliasHelper {
    /**
     * Applies merchant aliases to a list of [TransactionDetails].
     * If an alias exists for the merchant, it updates the description unless a manual change is detected.
     */
    fun applyAliases(
        transactions: List<TransactionDetails>,
        aliases: Map<String, String>,
    ): List<TransactionDetails> {
        return transactions.map { details ->
            val original = details.transaction.originalDescription
            val currentDesc = details.transaction.description
            val key = (original ?: currentDesc).lowercase(Locale.getDefault())
            val alias = aliases[key]

            val newDescription = if (alias != null) {
                // If it matches original, apply alias.
                // If it was already matching the alias, applying it changes nothing.
                // If it matches neither, it's a manual exception, so preserve currentDesc.
                if (currentDesc.equals(original, ignoreCase = true) || currentDesc.equals(alias, ignoreCase = true)) {
                    alias
                } else {
                    currentDesc
                }
            } else {
                currentDesc
            }
            details.copy(transaction = details.transaction.copy(description = newDescription))
        }
    }
}

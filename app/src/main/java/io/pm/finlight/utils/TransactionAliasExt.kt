package io.pm.finlight.utils

import io.pm.finlight.TransactionDetails
import java.util.Locale

/**
 * Applies aliases to a list of TransactionDetails.
 */
fun List<TransactionDetails>.applyAliases(aliases: Map<String, String>): List<TransactionDetails> {
    return this.map { details ->
        val original = details.transaction.originalDescription
        val currentDesc = details.transaction.description
        val key = (original ?: currentDesc).lowercase(Locale.getDefault())
        val alias = aliases[key]

        val newDescription =
            if (alias != null) {
                // Only apply the alias if the current description matches the original description.
                // If it does not match, it means the user manually changed it to something else,
                // or it was already evaluated to something else.
                if (original != null && currentDesc.equals(original, ignoreCase = true)) {
                    alias
                } else if (currentDesc.equals(alias, ignoreCase = true)) {
                    alias
                } else if (original == null && currentDesc.equals(key, ignoreCase = true)) {
                    // If there's no original description, and the current description is the key, apply alias.
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

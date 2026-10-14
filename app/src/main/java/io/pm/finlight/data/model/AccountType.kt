package io.pm.finlight

/**
 * Standard classification types for financial accounts.
 *
 * @property typeName The canonical string representation stored in [Account.type].
 */
enum class AccountType(val typeName: String) {
    BANK("Bank Account"),
    CREDIT_CARD("Credit Card"),
    CARD("Card"),
    PREPAID_CARD("Prepaid Card"),
    MEAL_CARD("Meal Card"),
    GENERAL("General"),
    OTHER("Other"),
    ;

    companion object {
        fun fromString(value: String?): AccountType {
            if (value.isNullOrBlank()) return OTHER
            val trimmed = value.trim()
            return entries.firstOrNull {
                it.name.equals(trimmed, ignoreCase = true) || it.typeName.equals(trimmed, ignoreCase = true)
            } ?: when (trimmed.lowercase()) {
                "bank", "savings account", "savings", "current account", "current", "checking" -> BANK
                "credit card", "credit" -> CREDIT_CARD
                "card" -> CARD
                "prepaid card", "prepaid" -> PREPAID_CARD
                "meal card", "meal card wallet", "meal card / wallet", "meal wallet" -> MEAL_CARD
                "general" -> GENERAL
                else -> OTHER
            }
        }
    }
}

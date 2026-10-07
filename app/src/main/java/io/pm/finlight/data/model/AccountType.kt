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
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) || it.typeName.equals(value, ignoreCase = true)
            } ?: OTHER
        }
    }
}

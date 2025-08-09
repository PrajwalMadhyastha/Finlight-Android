package io.pm.finlight

/**
 * Stores a user-defined rule to rename a parsed merchant name.
 * This is a plain data class, free of Android/Room annotations.
 *
 * @param originalName The name originally extracted by the parser's regex.
 * @param newName The name the user wants to see instead.
 */
data class MerchantRenameRule(
    val originalName: String,
    val newName: String
)

package io.pm.finlight

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Stores a user-defined rule to rename a parsed merchant name.
 * This is a plain data class, free of Android/Room annotations.
 *
 * @param originalName The name originally extracted by the parser's regex.
 * @param newName The name the user wants to see instead.
 */
@Serializable
@Entity(tableName = "merchant_rename_rules")
data class MerchantRenameRule(
    @PrimaryKey
    @ColumnInfo(name = "originalName", collate = ColumnInfo.NOCASE)
    val originalName: String,
    val newName: String
)

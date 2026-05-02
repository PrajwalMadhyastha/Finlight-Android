// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/Account.kt
// REASON: REFACTOR - A unique, case-insensitive index (`collate = NOCASE`) has
// been added to the 'name' column. This prevents duplicate account names with
// different casing (e.g., "SBI" and "sbi") from being created.
// =================================================================================
package io.pm.finlight

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "accounts",
    // --- NEW: Add a unique, case-insensitive index to the name column ---
    indices = [Index(value = ["name"], unique = true, name = "index_accounts_name_nocase")],
)
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    // --- UPDATED: Added COLLATE NOCASE to the column definition ---
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE)
    val name: String,
    val type: String,
    // The balance field is intentionally removed from the database entity.
    // It will be calculated on-the-fly.
)

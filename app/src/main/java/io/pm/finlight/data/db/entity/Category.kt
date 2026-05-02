// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/Category.kt
// REASON: REFACTOR - The index on the 'name' column has been updated with
// `collate = NOCASE`. This makes the uniqueness constraint case-insensitive at
// the database level, preventing duplicate categories like "Food" and "food".
// =================================================================================
package io.pm.finlight

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "categories",
    // --- UPDATED: Ensure the unique index on 'name' is case-insensitive ---
    indices = [Index(value = ["name"], unique = true, name = "index_categories_name_nocase")],
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    // --- UPDATED: Added COLLATE NOCASE to the column definition for robustness ---
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE)
    val name: String,
    // A key to identify the Material Icon for the category
    @ColumnInfo(name = "iconKey")
    val iconKey: String = "category", // Default icon
    // A key to identify the icon's background color
    @ColumnInfo(name = "colorKey")
    val colorKey: String = "gray", // Default color
)

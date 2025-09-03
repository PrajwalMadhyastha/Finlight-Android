// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/entity/Tag.kt
// REASON: FEATURE - Unified Travel Mode. A unique, case-insensitive index has
// been added to the 'name' column. This is a crucial step to ensure that
// auto-generated trip tags (e.g., "Goa Trip") do not create duplicates if the
// user enters a different casing (e.g., "goa trip").
// =================================================================================
package io.pm.finlight

import android.annotation.SuppressLint
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents a user-defined Tag (e.g., "Work Trip", "Vacation 2025", "Tax-Deductible").
 * Tags provide a flexible way to organize transactions outside of the rigid category system.
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true, name = "index_tags_name_nocase")]
)
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE)
    val name: String
)

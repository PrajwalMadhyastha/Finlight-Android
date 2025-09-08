// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/entity/Trip.kt
// REASON: FIX - Aligned the Room entity schema with the actual database
// schema created by the migrations. Added the `defaultValue` to the tripType
// column and marked the index on `tagId` as unique to resolve the
// "Migration didn't properly handle" runtime crash.
// =================================================================================
package io.pm.finlight.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.pm.finlight.Tag
import io.pm.finlight.TripType

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // --- FIX: Mark the index as unique to match the DB schema ---
    indices = [Index(value = ["tagId"], unique = true)]
)
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val tagId: Int,
    // --- FIX: Add default value to match the DB schema ---
    @ColumnInfo(defaultValue = "DOMESTIC")
    val tripType: TripType,
    val currencyCode: String?,
    val conversionRate: Float?
)
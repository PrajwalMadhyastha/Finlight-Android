package io.pm.finlight

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE // If the tag is deleted, the trip history is removed.
        )
    ],
    indices = [Index(value = ["tagId"], unique = true)] // A tag can only be associated with one trip.
)
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val tagId: Int
)
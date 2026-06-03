package io.pm.finlight

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "merchant_mappings")
data class MerchantMapping(
    // e.g., "AM-HDFCBK"
    @PrimaryKey
    val smsSender: String,
    // e.g., "McDonald's"
    val merchantName: String,
)

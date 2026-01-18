// =================================================================================
// FILE: ./analyzer/src/main/java/io/pm/finlight/analyzer/DatasetJsonModels.kt
// REASON: NEW FILE - This file defines the data classes for deserializing the
// JSON SMS dump, specifically for the DatasetGenerator. The class names are
// unique to avoid redeclaration conflicts with other tools in the same package.
// =================================================================================
package io.pm.finlight.analyzer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DatasetSmsDump(
    val smses: DatasetSmsesContainer
)

@Serializable
data class DatasetSmsesContainer(
    @SerialName("+@count")
    val count: String,
    val sms: List<DatasetSmsEntry>
)

@Serializable
data class DatasetSmsEntry(
    @SerialName("+@address")
    val address: String,
    @SerialName("+@date")
    val date: String,
    @SerialName("+@body")
    val body: String,
)

@Serializable
data class AndroidSmsItem(
    val address: String,
    val body: String,
    val date: Long,
    val ml_score: Double? = null,
    val ml_predicted_is_transaction: Boolean? = null,
    val ml_rule_ignore: Boolean? = null,
    val ml_ignore_reason: String? = null
)
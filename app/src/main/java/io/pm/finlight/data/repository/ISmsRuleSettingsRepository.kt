package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface ISmsRuleSettingsRepository {
    suspend fun saveSmsScanStartDate(date: Long)

    fun getSmsScanStartDate(): Flow<Long>

    suspend fun saveIgnoreRulesChecksum(checksum: Int)

    suspend fun getIgnoreRulesChecksum(): Int

    fun getDismissedMergeSuggestions(): Flow<Set<String>>

    suspend fun addDismissedMergeSuggestion(suggestionKey: String)
}

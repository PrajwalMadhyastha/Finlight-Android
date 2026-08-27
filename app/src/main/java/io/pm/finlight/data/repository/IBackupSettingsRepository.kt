package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IBackupSettingsRepository {
    suspend fun saveBackupEnabled(isEnabled: Boolean)

    fun getBackupEnabled(): Flow<Boolean>

    suspend fun saveAutoBackupEnabled(isEnabled: Boolean)

    fun getAutoBackupEnabled(): Flow<Boolean>

    suspend fun saveAutoBackupNotificationEnabled(isEnabled: Boolean)

    fun getAutoBackupNotificationEnabled(): Flow<Boolean>

    suspend fun saveLastBackupTimestamp(timestamp: Long)

    fun getLastBackupTimestamp(): Flow<Long>
}

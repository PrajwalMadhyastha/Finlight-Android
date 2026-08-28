package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface ISecuritySettingsRepository {
    suspend fun saveAppLockEnabled(isEnabled: Boolean)

    fun getAppLockEnabled(): Flow<Boolean>

    suspend fun savePrivacyModeEnabled(isEnabled: Boolean)

    fun getPrivacyModeEnabled(): Flow<Boolean>

    suspend fun saveSimulatorPrivacyModeEnabled(isEnabled: Boolean)

    fun getSimulatorPrivacyModeEnabled(): Flow<Boolean>
}

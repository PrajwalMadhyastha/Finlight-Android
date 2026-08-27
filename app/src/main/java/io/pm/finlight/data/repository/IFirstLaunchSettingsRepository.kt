package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IFirstLaunchSettingsRepository {
    fun getHasSeenOnboarding(): Flow<Boolean>

    suspend fun setHasSeenOnboarding(hasSeen: Boolean)

    fun getIsFirstLaunchComplete(): Flow<Boolean>

    suspend fun setFirstLaunchComplete()
}
